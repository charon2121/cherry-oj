"""Opt-in download facts, bounded across workers; never log server/error text."""
import json
import re
import ssl
import threading
import time
import urllib.error

MAX_BYTES = 256 << 10
MAX_PROGRESS = 16


def failure(error):
    if isinstance(error, urllib.error.HTTPError):
        return 'HTTP_ERROR'
    if isinstance(error, urllib.error.URLError):
        error = error.reason
    if isinstance(error, ssl.SSLCertVerificationError):
        return 'CERTIFICATE_REJECTED'
    if isinstance(error, ssl.SSLError):
        return 'TLS_ERROR'
    if isinstance(error, TimeoutError):
        return 'TIMEOUT'
    if isinstance(error, OSError):
        return 'IO_ERROR'
    if isinstance(error, ValueError):
        return 'REJECTED'
    return 'FAILED'


class Diagnostics:
    def __init__(self, enabled):
        self.enabled = enabled
        self.lock = threading.Lock()
        self.used = 0
        self.truncated = False

    def emit(self, event):
        if not self.enabled:
            return
        line = json.dumps(event, separators=(',', ':')) + '\n'
        marker = '{"event":"download-diagnostics-truncated"}\n'
        with self.lock:
            if self.truncated:
                return
            if self.used + len(line.encode()) > MAX_BYTES - len(marker):
                print(marker, end='', flush=True)
                self.truncated = True
                return
            self.used += len(line.encode())
            print(line, end='', flush=True)

    def request(self, number, name):
        return Request(self, number, name)


class Request:
    def __init__(self, diagnostics, number, name):
        self.diagnostics = diagnostics
        self.number = number
        self.name = name if isinstance(name, str) and re.fullmatch(r'[a-zA-Z0-9_.+%:~=-]{1,192}', name) else 'invalid-name'
        self.started = time.monotonic_ns()
        self.stage = 'prepare'
        self.read_bytes = 0
        self.last_progress = None
        self.progress_count = 0

    def event(self, event='download', **facts):
        self.diagnostics.emit(dict(event=event, requestId=self.number, name=self.name,
            stage=self.stage, readBytes=self.read_bytes,
            elapsedNs=max(0, time.monotonic_ns() - self.started), **facts))

    def phase(self, stage):
        self.stage = stage
        self.event()

    def connection(self, **facts):
        # The transport's elapsedNs measures one address attempt, not this request.
        self.stage = 'response_headers' if facts['outcome'] == 'CONNECTED' else 'connect'
        self.diagnostics.emit(dict(event='https-connect', requestId=self.number,
            name=self.name, requestElapsedNs=max(0, time.monotonic_ns() - self.started), **facts))

    def open(self, open_url, url):
        self.phase('open')
        options = dict(timeout=30)
        if self.diagnostics.enabled:
            options['observer'] = self
        response = open_url(url, **options)
        try:
            self.stage = 'headers'
            status = getattr(response, 'status', None)
            self.event(**({'httpStatus': status} if type(status) is int and 100 <= status <= 599 else {}))
        except BaseException:
            response.close()
            raise
        return response

    def progress(self, total):
        self.read_bytes = total
        now = time.monotonic_ns()
        if self.progress_count < MAX_PROGRESS and (self.last_progress is None or now - self.last_progress >= 5_000_000_000):
            self.event()
            self.last_progress = now
            self.progress_count += 1

    def __enter__(self):
        self.event()
        return self

    def __exit__(self, _kind, error, _traceback):
        if error is not None:
            facts = dict(outcome=failure(error))
            if isinstance(error, urllib.error.HTTPError) and type(error.code) is int and 100 <= error.code <= 599:
                facts['httpStatus'] = error.code
            self.event(**facts)
        return False
