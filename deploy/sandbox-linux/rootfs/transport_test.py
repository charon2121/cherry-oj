"""Connection faults must not weaken TLS or replay an HTTP request."""
from transport import AddressHTTPSConnection as HTTPSConnection
from http.client import HTTPResponse
import transport
from contextlib import redirect_stdout
import errno
import io
import os
from pathlib import Path
import socket
import ssl
import subprocess
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from unittest.mock import Mock, patch


ADDRESSES = [(socket.AF_INET, socket.SOCK_STREAM, socket.IPPROTO_TCP, '', (f'192.0.2.{n}', 443))
             for n in range(1, 3)]


class TransportTests(unittest.TestCase):
    def test_tls_timeout_tries_next_address_and_preserves_hostname(self):
        raw = [Mock(), Mock()]
        secured = Mock()
        context = Mock(check_hostname=True, verify_mode=ssl.CERT_REQUIRED)
        context.wrap_socket.side_effect = [TimeoutError('handshake'), secured]
        with patch.object(socket, 'getaddrinfo', return_value=ADDRESSES), patch.object(
                socket, 'socket', side_effect=raw):
            connection = HTTPSConnection('archive.ubuntu.com', timeout=30, context=context)
            try:
                connection.connect()
            except TimeoutError:
                self.fail('TLS timeout prevented trying the next resolved address')
        raw[0].close.assert_called()
        raw[1].connect.assert_called_once_with(ADDRESSES[1][4])
        self.assertIs(connection.sock, secured)
        self.assertEqual([call.kwargs['server_hostname'] for call in context.wrap_socket.call_args_list],
                         ['archive.ubuntu.com', 'archive.ubuntu.com'])
        connection.close()

    def test_failed_handshake_closes_tls_socket_before_next_address(self):
        raw = [Mock(), Mock()]
        secured = [Mock(), Mock()]
        secured[0].do_handshake.side_effect = ConnectionResetError(errno.ECONNRESET, 'private-error')
        context = Mock(check_hostname=True, verify_mode=ssl.CERT_REQUIRED)
        context.wrap_socket.side_effect = secured
        with patch.object(socket, 'getaddrinfo', return_value=ADDRESSES), patch.object(
                socket, 'socket', side_effect=raw), redirect_stdout(io.StringIO()) as log:
            connection = HTTPSConnection('archive.ubuntu.com', timeout=30, context=context)
            connection.connect()
        self.assertNotIn('private-error', log.getvalue())
        secured[0].close.assert_called_once()
        raw[0].close.assert_called_once()
        self.assertIs(connection.sock, secured[1])
        connection.close()

    def test_certificate_and_unknown_errors_stop_without_using_next_peer(self):
        for error in (ssl.SSLCertVerificationError('certificate'), ssl.SSLError('protocol'),
                      ValueError('unexpected'), OSError(5, 'not a connection error')):
            with self.subTest(error=type(error).__name__):
                context = Mock(check_hostname=True, verify_mode=ssl.CERT_REQUIRED)
                secured, raw = Mock(), Mock()
                context.wrap_socket.return_value = secured
                secured.do_handshake.side_effect = error
                with patch.object(socket, 'getaddrinfo', return_value=ADDRESSES), patch.object(
                        socket, 'socket', return_value=raw) as factory:
                    with self.assertRaises(type(error)):
                        HTTPSConnection('archive.ubuntu.com', timeout=30, context=context).connect()
                self.assertEqual(factory.call_count, 1)
                secured.close.assert_called_once()
                raw.close.assert_called_once()

    def test_unique_address_cap_and_all_failed_sockets_closed(self):
        addresses = [(*ADDRESSES[0][:4], (f'192.0.2.{n}', 443)) for n in range(1, 12)]
        raw = [Mock() for _ in range(8)]
        for sock in raw:
            sock.connect.side_effect = ConnectionRefusedError(errno.ECONNREFUSED, 'refused')
        context = Mock(check_hostname=True, verify_mode=ssl.CERT_REQUIRED)
        with patch.object(socket, 'getaddrinfo', return_value=[addresses[0], *addresses]), patch.object(
                socket, 'socket', side_effect=raw) as factory:
            with self.assertRaises(ConnectionRefusedError):
                HTTPSConnection('archive.ubuntu.com', timeout=30, context=context).connect()
        self.assertEqual(factory.call_count, 8)
        self.assertEqual([s.connect.call_args.args[0] for s in raw], [a[4] for a in addresses[:8]])
        for sock in raw: sock.close.assert_called_once()
        context.wrap_socket.assert_not_called()

    def test_total_deadline_includes_dns_and_attempt_deadline_includes_tcp(self):
        now = [0.0]
        raw, secured = Mock(), Mock()
        context = Mock(check_hostname=True, verify_mode=ssl.CERT_REQUIRED)
        context.wrap_socket.return_value = secured
        def tcp(_address): now[0] += 4
        def tls(): now[0] += 1; raise TimeoutError('handshake')
        raw.connect.side_effect = tcp
        secured.do_handshake.side_effect = tls
        addresses = [(*ADDRESSES[0][:4], (f'192.0.2.{n}', 443)) for n in range(1, 9)]
        with patch.object(transport.time, 'monotonic', side_effect=lambda: now[0]), patch.object(
                socket, 'getaddrinfo', return_value=addresses), patch.object(socket, 'socket', return_value=raw) as factory:
            with self.assertRaises(TimeoutError):
                HTTPSConnection('archive.ubuntu.com', timeout=30, context=context).connect()
        self.assertEqual(now[0], 30)
        self.assertEqual(factory.call_count, 6)
        self.assertTrue(all(c.args[0] == 1 for c in secured.settimeout.call_args_list))
        self.assertEqual(secured.close.call_count, 6)
        now[0] = 0
        def slow_dns(*_args, **_kwargs): now[0] = 31; return addresses
        with patch.object(transport.time, 'monotonic', side_effect=lambda: now[0]), patch.object(
                socket, 'getaddrinfo', side_effect=slow_dns), patch.object(socket, 'socket') as factory:
            with self.assertRaises(TimeoutError):
                HTTPSConnection('archive.ubuntu.com', timeout=30, context=context).connect()
            factory.assert_not_called()

    def test_insecure_context_proxy_and_invalid_deadline_rejected_before_dns(self):
        for kind in ('hostname', 'certificate', 'proxy', 'timeout'):
            with self.subTest(kind=kind):
                context = Mock(check_hostname=kind != 'hostname', verify_mode=ssl.CERT_NONE if kind == 'certificate' else ssl.CERT_REQUIRED)
                connection = HTTPSConnection('archive.ubuntu.com', timeout=31 if kind == 'timeout' else 30, context=context)
                if kind == 'proxy': connection.set_tunnel('archive.ubuntu.com')
                with patch.object(socket, 'getaddrinfo') as dns, self.assertRaises(ValueError):
                    connection.connect()
                dns.assert_not_called()

    def test_http_send_or_response_error_is_not_retried(self):
        for method in ('request', 'getresponse'):
            with self.subTest(method=method):
                request = urllib.request.Request('https://archive.ubuntu.com/ubuntu/')
                request.timeout = 30
                connection = Mock()
                getattr(connection, method).side_effect = ConnectionResetError(errno.ECONNRESET, 'reset after HTTP')
                with patch.object(transport, 'AddressHTTPSConnection', return_value=connection) as factory:
                    with self.assertRaises((urllib.error.URLError, ConnectionResetError)):
                        transport.AddressHTTPSHandler().https_open(request)
                self.assertEqual(factory.call_count, 1)
                self.assertEqual(connection.request.call_count, 1)
                connection.close.assert_called_once()

    def test_source_guard_and_redirect_boundaries(self):
        open_url = transport.source_urlopen('https://archive.ubuntu.com/ubuntu/')
        for url in ('http://archive.ubuntu.com/ubuntu/', 'https://other.invalid/',
                    'https://user:password@archive.ubuntu.com/', 'https://archive.ubuntu.com:444/', 'file:///etc/passwd'):
            with self.subTest(url=url), patch.object(socket, 'getaddrinfo') as dns:
                with self.assertRaises(ValueError): open_url(url)
                dns.assert_not_called()
        redirect = transport.SameOriginRedirect(('archive.ubuntu.com', 443))
        request = urllib.request.Request('https://archive.ubuntu.com/ubuntu/')
        for url in ('http://archive.ubuntu.com/', 'https://other.invalid/', 'https://secret@archive.ubuntu.com/'):
            response = Mock()
            with self.assertRaises(ValueError): redirect.redirect_request(request, response, 302, '', {}, url)
            response.close.assert_called_once()
            response.read.assert_not_called()
        response = Mock()
        response.read.return_value = b'x' * 65537
        with self.assertRaises(ValueError):
            redirect.redirect_request(request, response, 302, '', {}, 'https://archive.ubuntu.com/ubuntu/new')
        response.close.assert_called_once()
        response = io.BytesIO(b'small redirect body')
        self.assertEqual(redirect.redirect_request(request, response, 302, '', {},
            'https://archive.ubuntu.com/ubuntu/new').full_url, 'https://archive.ubuntu.com/ubuntu/new')

    def test_opt_in_ignores_proxy_environment(self):
        with patch.dict(os.environ, {'HTTPS_PROXY': 'http://private.invalid:99'}), patch.object(
                urllib.request, 'build_opener', wraps=urllib.request.build_opener) as build:
            transport.source_urlopen('https://archive.ubuntu.com/')
        self.assertEqual(build.call_args.args[0].proxies, {})

    def test_complete_redirect_path_never_drains_unbounded_or_truncated_body(self):
        for kind, headers, payload, accepted in (
                ('normal', b'Content-Length: 10\r\n', b'0123456789', True),
                ('chunked', b'Transfer-Encoding: chunked\r\n', b'a\r\n0123456789\r\n0\r\n\r\n', True),
                ('unknown-length', b'', b'0123456789', True),
                ('oversize', b'Content-Length: 65537\r\n', b'x' * 65537, False),
                ('truncated', b'Content-Length: 1000000000\r\n', b'0123456789', False)):
            with self.subTest(kind=kind):
                reads = []
                class TrackingBody(io.BytesIO):
                    def read(self, size=-1):
                        reads.append(size)
                        return super().read(size)
                body = TrackingBody(b'HTTP/1.1 302 Found\r\nLocation: /ubuntu/next\r\n' + headers + b'\r\n' + payload)
                response = HTTPResponse(Mock(makefile=Mock(return_value=body)))
                response.begin()
                request = urllib.request.Request('https://archive.ubuntu.com/ubuntu/')
                request.timeout = 30
                handler = transport.SameOriginRedirect(('archive.ubuntu.com', 443))
                handler.parent = Mock()
                if accepted:
                    handler.http_error_302(request, response, 302, 'Found', response.headers)
                    handler.parent.open.assert_called_once()
                else:
                    with self.assertRaises(ValueError):
                        handler.http_error_302(request, response, 302, 'Found', response.headers)
                    handler.parent.open.assert_not_called()
                self.assertTrue(response.isclosed())
                self.assertTrue(all(0 <= size <= 65537 for size in reads), reads)


class LocalTLSTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory(prefix='cherry-work054-tls-')
        cls.addClassCleanup(cls.directory.cleanup)
        cls.root = Path(cls.directory.name)
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
            '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost',
            '-keyout', str(cls.root / 'key.pem'), '-out', str(cls.root / 'cert.pem')],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
        subprocess.run(['openssl', 'req', '-new', '-key', str(cls.root / 'key.pem'), '-subj', '/CN=localhost',
            '-out', str(cls.root / 'request.pem')],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
        (cls.root / 'index').touch()
        (cls.root / 'serial').write_text('01\n')
        (cls.root / 'ca.cnf').write_text(f'''[ca]
default_ca = test
[test]
database = {cls.root}/index
serial = {cls.root}/serial
new_certs_dir = {cls.root}
certificate = {cls.root}/cert.pem
private_key = {cls.root}/key.pem
default_md = sha256
policy = policy
[policy]
commonName = supplied
''')
        subprocess.run(['openssl', 'ca', '-selfsign', '-batch', '-config', str(cls.root / 'ca.cnf'),
            '-startdate', '20200101000000Z', '-enddate', '20200102000000Z',
            '-in', str(cls.root / 'request.pem'), '-out', str(cls.root / 'expired.pem')],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)

    def test_real_tls_accepts_trusted_hostname_and_rejects_untrusted_mismatch_expiry(self):
        for kind in ('valid', 'untrusted', 'hostname', 'expired'):
            with self.subTest(kind=kind):
                cert = self.root / ('expired.pem' if kind == 'expired' else 'cert.pem')
                server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
                server_context.load_cert_chain(cert, self.root / 'key.pem')
                client_context = ssl.create_default_context()
                if kind != 'untrusted': client_context.load_verify_locations(cafile=cert)
                with socket.socket() as listener:
                    listener.bind(('127.0.0.1', 0))
                    listener.listen(1)
                    listener.settimeout(5)
                    port = listener.getsockname()[1]
                    server_errors = []
                    def serve():
                        try:
                            with listener.accept()[0] as raw:
                                raw.settimeout(5)
                                try:
                                    with server_context.wrap_socket(raw, server_side=True): pass
                                except ssl.SSLError: pass  # Client rejection is the expected negative case.
                        except BaseException as error: server_errors.append(type(error).__name__)
                    thread = threading.Thread(target=serve, daemon=True)
                    thread.start()
                    connection = HTTPSConnection('wrong.invalid' if kind == 'hostname' else 'localhost',
                                                 port=port, timeout=5, context=client_context)
                    addresses = [(socket.AF_INET, socket.SOCK_STREAM, socket.IPPROTO_TCP, '', ('127.0.0.1', port))]
                    try:
                        with patch.object(socket, 'getaddrinfo', return_value=addresses):
                            if kind == 'valid': connection.connect()
                            else:
                                with self.assertRaises(ssl.SSLCertVerificationError) as raised:
                                    connection.connect()
                                self.assertIn(raised.exception.verify_code, {'untrusted': (18, 19), 'hostname': (62,), 'expired': (10,)}[kind])
                    finally:
                        connection.close()
                        thread.join(6)
                    self.assertFalse(thread.is_alive())
                    self.assertEqual(server_errors, [])


if __name__ == '__main__':
    unittest.main()
