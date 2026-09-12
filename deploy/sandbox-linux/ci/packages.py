#!/usr/bin/env python3
"""CI-only package acquisition. Cached bytes are inputs, never evidence of a passed test."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import threading
import time
import urllib.parse

from report import ROOT, digest, git_sha, read_json

# Reuse archive naming and index parsing; the deployment CLI keeps its transport.
sys.path.insert(0, str(ROOT / 'deploy/sandbox-linux/rootfs'))
from download import BASE, archive_path, locations

PACKAGE_LIMIT = 100 << 20
INDEX_LIMIT = 32 << 20
TOTAL_SECONDS = 600
ATTEMPT_SECONDS = 120
RETRY_CODES = {18, 28, 56}
SCRIPT_PATHS = ('deploy/sandbox-linux/ci/packages.py', 'deploy/sandbox-linux/ci/report.py',
                'deploy/sandbox-linux/rootfs/download.py', 'deploy/sandbox-linux/rootfs/diagnostics.py')


def records(lock):
    data = read_json(lock)
    items = data.get('packages')
    if data.get('version') != 1 or data.get('architecture') != 'amd64' or not isinstance(items, list) or not 0 < len(items) <= 256:
        raise ValueError('invalid package lock')
    seen = set()
    for item in items:
        name = item.get('file', '')
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.+%:~-]*\.deb', name) or name in seen:
            raise ValueError('invalid or duplicate package filename')
        if not re.fullmatch(r'[0-9a-f]{64}', item.get('sha256', '')):
            raise ValueError('invalid package digest')
        if not re.fullmatch(r'[a-z0-9][a-z0-9+.-]*', item.get('package', '')) or item.get('architecture') not in ('amd64', 'all'):
            raise ValueError('invalid package identity')
        if not re.fullmatch(r'[A-Za-z0-9.+:~%-]+', item.get('version', '')):
            raise ValueError('invalid package version')
        seen.add(name)
    return items


def script_digest():
    value = hashlib.sha256()
    for name in SCRIPT_PATHS:
        value.update(name.encode() + b'\0' + digest(ROOT / name).encode() + b'\0')
    return value.hexdigest()


def cache_key(lock):
    records(lock)
    return 'sandbox-packages-v1-ubuntu24-amd64-' + digest(lock) + '-' + script_digest()


@contextmanager
def directory_fd(path):
    """Walk from / without following links, including links in parent components."""
    path = Path(os.path.abspath(path))
    fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY)
    try:
        for name in path.parts[1:]:
            next_fd = os.open(name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = next_fd
        yield fd
    finally:
        os.close(fd)


def identity(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_nlink,
            info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def copy_verified(lock, source, output, check=lambda: None):
    """Hash the bytes actually copied to an owned directory; publish only a complete set."""
    check()
    items = records(lock)
    names = {item['file'] for item in items}
    output = Path(output)
    if output.exists() or output.is_symlink():
        raise ValueError('refusing existing package output')
    total = 0
    with directory_fd(source) as directory, tempfile.TemporaryDirectory(prefix='.packages-', dir=output.parent) as scratch:
        stage = Path(scratch) / 'complete'
        stage.mkdir(mode=0o700)
        if set(os.listdir(directory)) != names:
            raise ValueError('package set differs from lock')
        for item in items:
            check()
            name = item['file']
            fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
            with os.fdopen(fd, 'rb') as stream, (stage / name).open('xb') as target:
                before = os.fstat(stream.fileno())
                if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1 or before.st_size > PACKAGE_LIMIT:
                    raise ValueError('non-regular, hardlinked or oversized package')
                value = hashlib.sha256()
                size = 0
                while chunk := stream.read(65536):
                    check()
                    size += len(chunk)
                    if size > PACKAGE_LIMIT:
                        raise ValueError('package exceeds limit')
                    value.update(chunk)
                    target.write(chunk)
                after = os.fstat(stream.fileno())
                current = os.stat(name, dir_fd=directory, follow_symlinks=False)
                if identity(before) != identity(after) or identity(after) != identity(current) or size != before.st_size:
                    raise ValueError('package changed during validation')
                if value.hexdigest() != item['sha256']:
                    raise ValueError('package SHA256 mismatch: ' + name)
                total += size
            (stage / name).chmod(0o400)
        if set(os.listdir(directory)) != names:
            raise ValueError('package set changed during validation')
        check()
        stage.rename(output)
    return dict(packages=len(items), bytes=total, packageLock=digest(lock))


class Events:
    """Fixed fields only; concurrent attempts share a strict diagnostic budget."""
    def __init__(self):
        self.lock = threading.Lock()
        self.used = 0
        self.requests = 0

    def emit(self, event, **fields):
        line = json.dumps(dict(event=event, **fields), sort_keys=True) + '\n'
        with self.lock:
            if self.used + len(line.encode()) > 256 << 10:
                raise RuntimeError('package diagnostic budget exhausted')
            self.used += len(line.encode())
            print(line, end='', flush=True)


class Curl:
    """curl owns HTTP/TLS; this class bounds attempts, output and child lifetimes."""
    def __init__(self, deadline, stopped, events):
        self.deadline, self.stopped, self.events = deadline, stopped, events
        self.binary = shutil.which('curl')
        if not self.binary:
            raise RuntimeError('curl is required')
        # An allowlist excludes proxy, CA, credentials and curl configuration overrides.
        self.env = {'PATH': '/usr/bin:/bin', 'LANG': 'C', 'LC_ALL': 'C'}

    def check(self):
        if self.stopped.is_set():
            raise InterruptedError('package acquisition cancelled')
        if time.monotonic() >= self.deadline:
            raise TimeoutError('package acquisition deadline exceeded')

    def version(self):
        self.check()
        result = subprocess.run([self.binary, '-q', '--version'], env=self.env,
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                timeout=min(5, self.deadline - time.monotonic()), check=True)
        text = result.stdout.decode('ascii', errors='replace')
        match = re.match(r'curl (\d+)\.(\d+)\.(\d+)', text)
        if len(result.stdout) > 8192 or not match or tuple(map(int, match.groups())) < (7, 76, 0) or 'https' not in text:
            raise RuntimeError('unsupported curl version/features')
        self.events.emit('curl', version=match.group(0))

    def attempt(self, url, target, limit):
        self.check()
        end = min(self.deadline, time.monotonic() + ATTEMPT_SECONDS)
        argv = [self.binary, '-q', '--silent', '--show-error', '--fail', '--proto', '=https',
                '--noproxy', '*', '--connect-timeout', '30', '--max-time', str(max(.001, end - time.monotonic())),
                '--speed-limit', '65536', '--speed-time', '30', '--max-filesize', str(limit),
                '--dump-header', '/dev/stderr', '--write-out', '%{stderr}%{http_code}', '--url', url]
        size, error = 0, bytearray()
        with target.open('xb') as output:
            process = subprocess.Popen(argv, env=self.env, stdin=subprocess.DEVNULL,
                                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            try:
                with selectors.DefaultSelector() as selector:
                    selector.register(process.stdout, selectors.EVENT_READ, 'body')
                    selector.register(process.stderr, selectors.EVENT_READ, 'status')
                    while selector.get_map():
                        self.check()
                        if time.monotonic() >= end:
                            return 28, 0, size
                        for key, _ in selector.select(min(.1, max(0, end - time.monotonic()))):
                            chunk = os.read(key.fileobj.fileno(), 65536)
                            if not chunk:
                                selector.unregister(key.fileobj)
                            elif key.data == 'body':
                                size += len(chunk)
                                if size > limit:
                                    raise ValueError('download exceeds byte limit')
                                output.write(chunk)
                            else:
                                error.extend(chunk)
                                if len(error) > 65536:
                                    raise ValueError('curl diagnostic exceeds limit')
                                statuses = re.findall(rb'(?:^|\n)HTTP/\S+ ([0-9]{3})(?: |\r?\n)', error)
                                if statuses and int(statuses[-1]) >= 300:
                                    return 22, int(statuses[-1]), size
                    code = process.wait(timeout=max(.001, end - time.monotonic()))
                match = re.search(rb'(\d{3})$', error)
                return code, int(match.group(1)) if match else 0, size
            finally:
                if process.poll() is None:
                    process.kill()
                process.wait(timeout=5)
                process.stdout.close()
                process.stderr.close()

    def fetch(self, url, target, limit, expected=None):
        parsed = urllib.parse.urlsplit(url)
        origin = urllib.parse.urlsplit(BASE)
        if parsed.scheme != 'https' or parsed.netloc != origin.netloc or parsed.query or parsed.fragment:
            raise ValueError('download must use reviewed HTTPS origin')
        for number in range(1, 4):
            self.check()
            started = time.monotonic_ns()
            with self.events.lock:
                self.events.requests += 1
            try:
                code, status, size = self.attempt(url, target, limit)
                self.events.emit('attempt', name=target.name, attempt=number, code=code, status=status,
                                 bytes=size, elapsedNs=time.monotonic_ns() - started)
                if status not in (0, 200):
                    raise RuntimeError('HTTP response rejected')
                if code == 0:
                    if status != 200:
                        raise RuntimeError('missing successful HTTP status')
                    if expected is not None and digest(target) != expected:
                        raise ValueError('locked package SHA256 mismatch')
                    return
                if code not in RETRY_CODES or number == 3:
                    raise RuntimeError('curl transfer failed: ' + str(code))
            except BaseException:
                target.unlink(missing_ok=True)
                raise
            target.unlink(missing_ok=True)
            if self.stopped.wait(min(2 ** number, max(0, self.deadline - time.monotonic()))):
                self.check()
        raise RuntimeError('unreachable download state')


def acquire(lock, output, events, stopped):
    items = records(lock)
    deadline = time.monotonic() + TOTAL_SECONDS
    client = Curl(deadline, stopped, events)
    client.version()
    with tempfile.TemporaryDirectory(prefix='.download-', dir=output.parent) as scratch:
        root = Path(scratch)
        indexes = root / 'indexes'
        indexes.mkdir()
        raw = root / 'raw'
        raw.mkdir()

        @contextmanager
        def open_index(url, timeout=30):
            del timeout  # curl and the common acquisition deadline own timeouts.
            suite = url.split('/dists/', 1)[1].split('/', 1)[0]
            path = indexes / (suite + '-Packages.xz')
            client.fetch(url, path, INDEX_LIMIT)
            with path.open('rb') as stream:
                yield stream

        paths = locations(open_index)

        def fetch(item):
            client.check()
            path = archive_path(item, paths[item['package']])
            client.fetch(BASE + urllib.parse.quote(str(path)), raw / item['file'], PACKAGE_LIMIT, item['sha256'])

        pool = ThreadPoolExecutor(max_workers=4)
        try:
            list(pool.map(fetch, items))
        except BaseException:
            stopped.set()
            raise
        finally:
            pool.shutdown(wait=True, cancel_futures=True)
        client.check()
        result = copy_verified(lock, raw, output, client.check)
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lock', type=Path, required=True)
    parser.add_argument('--key', action='store_true')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--source', type=Path, help='untrusted complete package set; no network access')
    args = parser.parse_args()
    if args.key:
        print(cache_key(args.lock))
        return
    if args.output is None:
        parser.error('--output is required')
    output = args.output
    if output.exists() or output.is_symlink():
        raise ValueError('refusing existing package output')
    events, stopped = Events(), threading.Event()
    previous = {sig: signal.signal(sig, lambda *_: stopped.set()) for sig in (signal.SIGTERM, signal.SIGINT)}
    def deadline_expired(*_):
        stopped.set()
        raise TimeoutError('package preparation deadline exceeded')
    previous_alarm = signal.signal(signal.SIGALRM, deadline_expired)
    previous_timer = signal.setitimer(signal.ITIMER_REAL, TOTAL_SECONDS)
    def check_cancelled():
        if stopped.is_set():
            raise InterruptedError('package preparation cancelled')
    started = time.monotonic_ns()
    published = None
    try:
        with tempfile.TemporaryDirectory(prefix='.preparation-', dir=output.parent) as scratch:
            staged = Path(scratch) / 'packages'
            result = copy_verified(args.lock, args.source, staged, check_cancelled) if args.source else acquire(args.lock, staged, events, stopped)
            check_cancelled()
            facts = dict(source='existing' if args.source else 'cold', sourceSha=git_sha(),
                         scriptsSha=script_digest(), requests=events.requests,
                         elapsedNs=time.monotonic_ns() - started, **result)
            info = staged.lstat()
            published = (info.st_dev, info.st_ino)
            check_cancelled()
            staged.rename(output)
            check_cancelled()
            events.emit('verified', **facts)
            check_cancelled()
    except BaseException as error:
        # The caller may replace the path. Never delete a directory merely because its name matches.
        if published is not None:
            try:
                info = output.lstat()
            except FileNotFoundError:
                pass
            else:
                if stat.S_ISDIR(info.st_mode) and (info.st_dev, info.st_ino) == published:
                    shutil.rmtree(output)
        events.emit('failed', kind=type(error).__name__, requests=events.requests,
                    elapsedNs=time.monotonic_ns() - started)
        raise
    finally:
        signal.setitimer(signal.ITIMER_REAL, *previous_timer)
        signal.signal(signal.SIGALRM, previous_alarm)
        for sig, handler in previous.items():
            signal.signal(sig, handler)


if __name__ == '__main__':
    main()
