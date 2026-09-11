"""Opt-in direct HTTPS: try DNS peers only before sending any HTTP request."""
import errno
from contextvars import ContextVar
from http.client import HTTPSConnection
import ipaddress
import json
import math
import socket
import ssl
import time
import urllib.parse
import urllib.request

CONNECT_SECONDS = 30
ADDRESS_SECONDS = 5
MAX_ADDRESSES = 8
OBSERVER = ContextVar('download_observer', default=None)
NETWORK_ERRORS = {errno.ECONNREFUSED, errno.ECONNRESET, errno.ECONNABORTED,
                  errno.ENETUNREACH, errno.EHOSTUNREACH, errno.ETIMEDOUT}


def retryable(error):
    # SSL errors (especially certificate errors) must never be retried as OSError.
    return not isinstance(error, ssl.SSLError) and (
        isinstance(error, TimeoutError) or isinstance(error, OSError) and error.errno in NETWORK_ERRORS)


def remaining(deadline):
    seconds = deadline - time.monotonic()
    if seconds <= 0:
        raise TimeoutError('HTTPS connection deadline expired')
    return seconds


class AddressHTTPSConnection(HTTPSConnection):
    def connect(self):
        if self._tunnel_host or self.source_address is not None:
            raise ValueError('address failover requires a direct HTTPS connection')
        if not self._context.check_hostname or self._context.verify_mode != ssl.CERT_REQUIRED:
            raise ValueError('address failover requires certificate and hostname verification')
        if type(self.timeout) not in (int, float) or not math.isfinite(self.timeout) or not 0 < self.timeout <= CONNECT_SECONDS:
            raise ValueError('invalid HTTPS connection timeout')
        deadline = time.monotonic() + self.timeout
        # getaddrinfo itself is not interruptible by this socket deadline. The CI
        # command has a separate hard deadline; reject an overdue DNS result here.
        addresses = socket.getaddrinfo(self.host, self.port, type=socket.SOCK_STREAM)
        remaining(deadline)
        seen = set()
        last_error = None
        for family, kind, protocol, _, address in addresses:
            if family not in (socket.AF_INET, socket.AF_INET6):
                continue
            peer = str(ipaddress.ip_address(address[0]))
            identity = (family, address)
            if identity in seen:
                continue
            if len(seen) == MAX_ADDRESSES:
                break
            remaining(deadline)
            seen.add(identity)
            started = time.monotonic()
            attempt_deadline = min(deadline, started + ADDRESS_SECONDS)
            raw, secured = None, None
            stage, outcome = 'connect', 'FAILED'
            try:
                timeout = remaining(attempt_deadline)
                raw = socket.socket(family, kind, protocol)
                raw.settimeout(timeout)
                raw.connect(address)
                raw.settimeout(remaining(attempt_deadline))
                stage = 'tls'
                secured = self._context.wrap_socket(raw, server_hostname=self.host,
                                                    do_handshake_on_connect=False)
                secured.settimeout(remaining(attempt_deadline))
                secured.do_handshake()
                remaining(attempt_deadline)
                secured.settimeout(self.timeout)  # Restore the existing response read timeout.
                self.sock = secured
                outcome = 'CONNECTED'
                return
            except BaseException as error:
                if isinstance(error, ssl.SSLCertVerificationError):
                    outcome = 'CERTIFICATE_REJECTED'
                elif retryable(error):
                    outcome = 'TIMEOUT' if isinstance(error, TimeoutError) else 'NETWORK_ERROR'
                    last_error = error
                    continue
                raise
            finally:
                if outcome != 'CONNECTED':
                    if secured is not None:
                        secured.close()
                    if raw is not None:
                        raw.close()
                # Fixed classifications only; no exception text, URL path, headers or environment.
                facts = dict(host=self.host, peer=peer,
                    attempt=len(seen), stage=stage, outcome=outcome,
                    elapsedNs=max(0, int((time.monotonic() - started) * 1e9)))
                observer = OBSERVER.get()
                if observer is not None:
                    observer.connection(**facts)
                else:
                    print(json.dumps(dict(event='https-connect', **facts)), flush=True)
        if last_error is not None:
            raise last_error
        raise OSError('no usable HTTPS peer addresses')


class AddressHTTPSHandler(urllib.request.HTTPSHandler):
    def https_open(self, request):
        return self.do_open(AddressHTTPSConnection, request, context=self._context)


def origin(url):
    parts = urllib.parse.urlsplit(url)
    if parts.scheme != 'https' or not parts.hostname or parts.username is not None or parts.password is not None:
        raise ValueError('direct HTTPS origin required')
    return parts.hostname, parts.port or 443


class SameOriginRedirect(urllib.request.HTTPRedirectHandler):
    def __init__(self, expected):
        self.expected = expected

    def redirect_request(self, request, response, code, message, headers, new_url):
        try:
            if origin(new_url) != self.expected:
                raise ValueError('HTTPS redirect leaves the package source')
            # urllib normally drains redirect bodies without a size limit. Bound
            # that drain before delegating its redirect/method/loop checks.
            if len(response.read(65_537)) > 65_536:
                raise ValueError('HTTPS redirect body exceeds limit')
            if getattr(response, 'length', None) not in (None, 0):
                raise ValueError('truncated HTTPS redirect body')
        except BaseException:
            response.close()
            raise
        # A bounded HTTPResponse.read(n) may short-read without raising. Close it
        # before urllib's unbounded drain so no underlying bytes can be read there.
        response.close()
        return super().redirect_request(request, response, code, message, headers, new_url)


def source_urlopen(base):
    """Build a source-scoped opener without system proxy inheritance or TLS overrides."""
    expected = origin(base)
    context = ssl.create_default_context()
    context.set_alpn_protocols(['http/1.1'])
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}),
        AddressHTTPSHandler(context=context), SameOriginRedirect(expected))

    def open_url(url, timeout=CONNECT_SECONDS, *, observer=None):
        if origin(url) != expected:
            raise ValueError('HTTPS request leaves the package source')
        token = OBSERVER.set(observer)
        try:
            return opener.open(url, timeout=timeout)
        finally:
            OBSERVER.reset(token)

    return open_url
