"""Exercise untrusted package inputs and bounded transfer failure paths, without public network."""
from contextlib import redirect_stdout
import hashlib
import http.server
import lzma
import ssl
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

import packages


class PackageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.source = self.root / 'source'
        self.source.mkdir()
        self.name = 'cpp_1_amd64.deb'
        self.content = b'locked package'
        (self.source / self.name).write_bytes(self.content)
        self.lock = self.root / 'lock.json'
        self.data = dict(version=1, architecture='amd64', packages=[dict(package='cpp', version='1',
                        architecture='amd64', file=self.name, sha256=hashlib.sha256(self.content).hexdigest())])
        self.save()
        self.output = self.root / 'output'

    def save(self):
        self.lock.write_text(json.dumps(self.data))

    def test_copy_uses_verified_bytes_and_independent_inode(self):
        result = packages.copy_verified(self.lock, self.source, self.output)
        self.assertEqual(result['packages'], 1)
        self.assertEqual((self.output / self.name).read_bytes(), self.content)
        self.assertNotEqual((self.source / self.name).stat().st_ino, (self.output / self.name).stat().st_ino)
        (self.source / self.name).write_bytes(b'changed after copy')
        self.assertEqual((self.output / self.name).read_bytes(), self.content)

    def test_invalid_sets_and_digest_fail_without_publishing(self):
        for mutation in ('missing', 'extra', 'digest'):
            with self.subTest(mutation=mutation):
                path = self.source / self.name
                path.write_bytes(self.content)
                if mutation == 'missing': path.unlink()
                elif mutation == 'extra': (self.source / 'extra').touch()
                else: path.write_bytes(b'wrong')
                with self.assertRaises(ValueError): packages.copy_verified(self.lock, self.source, self.output)
                self.assertFalse(self.output.exists())
                (self.source / 'extra').unlink(missing_ok=True)

    def test_links_special_files_and_oversize_rejected(self):
        path = self.source / self.name
        for kind in ('symlink', 'hardlink', 'fifo', 'large'):
            with self.subTest(kind=kind):
                path.unlink(missing_ok=True)
                other = self.root / kind
                other.write_bytes(self.content)
                if kind == 'symlink': path.symlink_to(other)
                elif kind == 'hardlink': os.link(other, path)
                elif kind == 'fifo': os.mkfifo(path)
                else: path.write_bytes(self.content)
                with patch.object(packages, 'PACKAGE_LIMIT', 4 if kind == 'large' else 1000):
                    with self.assertRaises((ValueError, OSError)):
                        packages.copy_verified(self.lock, self.source, self.output)
                self.assertFalse(self.output.exists())

    def test_parent_symlink_and_existing_output_rejected(self):
        alias = self.root / 'alias'
        alias.symlink_to(self.source, target_is_directory=True)
        with self.assertRaises(OSError): packages.copy_verified(self.lock, alias, self.output)
        self.output.mkdir()
        (self.output / 'owned-by-user').write_text('keep')
        with self.assertRaises(ValueError): packages.copy_verified(self.lock, self.source, self.output)
        self.assertEqual((self.output / 'owned-by-user').read_text(), 'keep')

    def test_replacement_during_copy_rejected(self):
        original = os.fstat
        replaced = False
        def fstat(fd):
            nonlocal replaced
            info = original(fd)
            if not replaced and info.st_ino == (self.source / self.name).stat().st_ino:
                replaced = True
                (self.source / self.name).rename(self.root / 'old')
                (self.source / self.name).write_bytes(self.content)
            return info
        with patch.object(packages.os, 'fstat', side_effect=fstat):
            with self.assertRaises(ValueError): packages.copy_verified(self.lock, self.source, self.output)
        self.assertFalse(self.output.exists())

    def test_lock_rejects_duplicate_path_and_digest_changes_key(self):
        original = packages.cache_key(self.lock)
        self.data['packages'][0]['sha256'] = '0' * 64
        self.save()
        self.assertNotEqual(packages.cache_key(self.lock), original)
        self.data['packages'].append(dict(self.data['packages'][0]))
        self.save()
        with self.assertRaises(ValueError): packages.records(self.lock)
        self.data['packages'] = [dict(self.data['packages'][0], file='../escape.deb')]
        self.save()
        with self.assertRaises(ValueError): packages.records(self.lock)

    def client(self):
        return packages.Curl(time.monotonic() + 10, threading.Event(), packages.Events())

    def test_retry_only_transient_transfer_errors_and_keep_attempt_evidence(self):
        client = self.client()
        calls = []
        def attempt(url, target, limit):
            calls.append(url)
            target.write_bytes(self.content if len(calls) == 2 else b'partial')
            return (0 if len(calls) == 2 else 18), 200, target.stat().st_size
        with patch.object(client, 'attempt', side_effect=attempt), patch.object(client.stopped, 'wait', return_value=False), redirect_stdout(io.StringIO()) as log:
            client.fetch(packages.BASE + 'file', self.output, 1000, hashlib.sha256(self.content).hexdigest())
        self.assertEqual(len(calls), 2)
        self.assertEqual(self.output.read_bytes(), self.content)
        self.assertEqual([json.loads(line)['code'] for line in log.getvalue().splitlines()], [18, 0])

    def test_terminal_errors_not_retried(self):
        for code, status in ((60, 0), (22, 404), (28, 403), (0, 302), (0, 200), (63, 200)):
            with self.subTest(code=code, status=status):
                client = self.client()
                def attempt(*_):
                    self.output.write_bytes(b'wrong digest')
                    return code, status, 12
                with patch.object(client, 'attempt', side_effect=attempt) as run, redirect_stdout(io.StringIO()):
                    with self.assertRaises((ValueError, RuntimeError)):
                        client.fetch(packages.BASE + 'file', self.output, 1000, '0' * 64)
                self.assertEqual(run.call_count, 1)
                self.assertFalse(self.output.exists())

    def test_attempt_count_common_deadline_and_origin_guard(self):
        client = self.client()
        with patch.object(client, 'attempt', return_value=(28, 0, 0)) as run, patch.object(client.stopped, 'wait', return_value=False), redirect_stdout(io.StringIO()):
            with self.assertRaises(RuntimeError): client.fetch(packages.BASE + 'file', self.output, 1000)
        self.assertEqual(run.call_count, 3)
        for url in ('http://archive.ubuntu.com/file', 'https://evil.example/file'):
            with self.assertRaises(ValueError): client.fetch(url, self.output, 1000)
        client.deadline = time.monotonic() - 1
        with self.assertRaises(TimeoutError): client.fetch(packages.BASE + 'file', self.output, 1000)

    def fake_curl(self, text):
        executable = self.root / 'curl'
        executable.write_text('#!' + sys.executable + '\n' + text)
        executable.chmod(0o700)
        client = self.client()
        client.binary = str(executable)
        return client

    def test_real_child_output_cap_and_reap(self):
        pid = self.root / 'pid'
        client = self.fake_curl(f'import os,time\nopen({str(pid)!r},"w").write(str(os.getpid()))\nos.write(1,b"x"*65536)\ntime.sleep(20)\n')
        with self.assertRaises(ValueError): client.attempt(packages.BASE + 'file', self.output, 10)
        with self.assertRaises(ProcessLookupError): os.kill(int(pid.read_text()), 0)

    def test_real_child_cancellation_reaps_without_waiting_for_transfer(self):
        pid = self.root / 'pid'
        client = self.fake_curl(f'import os,time\nopen({str(pid)!r},"w").write(str(os.getpid()))\ntime.sleep(20)\n')
        def cancel_after_start():
            end = time.monotonic() + 5
            while not pid.exists() and time.monotonic() < end:
                time.sleep(.01)
            client.stopped.set()
        timer = threading.Thread(target=cancel_after_start)
        timer.start()
        started = time.monotonic()
        try:
            with self.assertRaises(InterruptedError): client.attempt(packages.BASE + 'file', self.output, 1000)
        finally: timer.join()
        self.assertLess(time.monotonic() - started, 6)
        with self.assertRaises(ProcessLookupError): os.kill(int(pid.read_text()), 0)

    def test_real_child_attempt_deadline_and_scrubbed_environment(self):
        client = self.fake_curl('import os,time\nassert "HTTPS_PROXY" not in os.environ\nassert "CURL_CA_BUNDLE" not in os.environ\ntime.sleep(20)\n')
        with patch.object(packages, 'ATTEMPT_SECONDS', .2):
            code, _, _ = client.attempt(packages.BASE + 'file', self.output, 1000)
        self.assertEqual(code, 28)

    def test_existing_source_never_constructs_network_client(self):
        with patch('sys.argv', ['packages.py', '--lock', str(self.lock), '--source', str(self.source), '--output', str(self.output)]), patch.object(packages, 'Curl', side_effect=AssertionError('network')), redirect_stdout(io.StringIO()) as log:
            packages.main()
        self.assertEqual(json.loads(log.getvalue().splitlines()[-1])['requests'], 0)

    def test_acquire_parses_indexes_and_limits_parallel_transfers(self):
        for number in range(1, 8):
            item = dict(self.data['packages'][0], package='cpp' + str(number), file=f'cpp{number}_1_amd64.deb')
            self.data['packages'].append(item)
        self.save()
        index = ''.join(f"Package: {item['package']}\nFilename: pool/main/c/cpp/{item['file']}\n\n" for item in self.data['packages'])
        active = peak = 0
        mutex = threading.Lock()
        urls = []
        def fetch(client, url, target, limit, expected=None):
            nonlocal active, peak
            urls.append(url)
            if target.name.endswith('.xz'):
                target.write_bytes(lzma.compress(index.encode()))
                return
            with mutex:
                active += 1
                peak = max(peak, active)
            try:
                time.sleep(.03)
                target.write_bytes(self.content)
            finally:
                with mutex: active -= 1
        with patch.object(packages.Curl, 'version'), patch.object(packages.Curl, 'fetch', fetch):
            result = packages.acquire(self.lock, self.output, packages.Events(), threading.Event())
        self.assertEqual(result['packages'], 8)
        self.assertEqual(len(urls), 10)
        self.assertGreaterEqual(peak, 2)
        self.assertLessEqual(peak, 4)
        self.assertEqual(set(p.name for p in self.output.iterdir()), {item['file'] for item in self.data['packages']})
        self.assertFalse(list(self.root.glob('.download-*')))

    def test_failed_acquire_cancels_peers_and_removes_partial_files(self):
        stopped = threading.Event()
        def fail(*_args, **_kwargs):
            raise ValueError('bad package')
        with patch.object(packages.Curl, 'version'), patch.object(packages, 'locations', return_value={'cpp': 'pool/main/c/cpp/file.deb'}), patch.object(packages.Curl, 'fetch', side_effect=fail):
            with self.assertRaises(ValueError): packages.acquire(self.lock, self.output, packages.Events(), stopped)
        self.assertTrue(stopped.is_set())
        self.assertFalse(self.output.exists())
        self.assertFalse(list(self.root.glob('.download-*')))

    def test_cli_deadline_interrupts_non_network_preparation(self):
        def slow(*_args): time.sleep(10)
        with patch('sys.argv', ['packages.py', '--lock', str(self.lock), '--output', str(self.output)]), patch.object(packages, 'acquire', side_effect=slow), patch.object(packages, 'TOTAL_SECONDS', .05), redirect_stdout(io.StringIO()):
            with self.assertRaises(TimeoutError): packages.main()
        self.assertEqual(signal.getitimer(signal.ITIMER_REAL), (0.0, 0.0))

    def test_final_evidence_failure_removes_only_owned_publication(self):
        emit = packages.Events.emit
        for replacement in (False, True):
            with self.subTest(replacement=replacement):
                def fail(events, event, **fields):
                    if event == 'verified':
                        if replacement:
                            self.output.rename(self.root / 'original-owned')
                            self.output.mkdir()
                            (self.output / 'user-file').write_text('preserve')
                        raise RuntimeError('evidence failure')
                    return emit(events, event, **fields)
                with patch('sys.argv', ['packages.py', '--lock', str(self.lock), '--source', str(self.source), '--output', str(self.output)]), patch.object(packages.Events, 'emit', fail), redirect_stdout(io.StringIO()):
                    with self.assertRaises(RuntimeError): packages.main()
                if replacement:
                    self.assertEqual((self.output / 'user-file').read_text(), 'preserve')
                else:
                    self.assertFalse(self.output.exists())
                self.assertFalse(list(self.root.glob('.preparation-*')))

    def test_cancel_immediately_after_publication_cleans_owned_output(self):
        rename = Path.rename
        def cancel(path, target):
            result = rename(path, target)
            if target == self.output:
                os.kill(os.getpid(), signal.SIGTERM)
            return result
        with patch('sys.argv', ['packages.py', '--lock', str(self.lock), '--source', str(self.source), '--output', str(self.output)]), patch.object(Path, 'rename', cancel), redirect_stdout(io.StringIO()):
            with self.assertRaises(InterruptedError): packages.main()
        self.assertFalse(self.output.exists())
        self.assertFalse(list(self.root.glob('.preparation-*')))

    def test_metadata_failure_before_publication_removes_staging(self):
        with patch('sys.argv', ['packages.py', '--lock', str(self.lock), '--source', str(self.source), '--output', str(self.output)]), patch.object(packages, 'git_sha', side_effect=RuntimeError('metadata failure')), redirect_stdout(io.StringIO()):
            with self.assertRaises(RuntimeError): packages.main()
        self.assertFalse(self.output.exists())
        self.assertFalse(list(self.root.glob('.preparation-*')))


class CurlHTTPSTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory(prefix='cherry-curl-tls-')
        cls.addClassCleanup(cls.directory.cleanup)
        cls.root = Path(cls.directory.name)
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                        '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost',
                        '-keyout', str(cls.root / 'key.pem'), '-out', str(cls.root / 'cert.pem')],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)

    def test_real_curl_tls_http_and_partial_body_recovery(self):
        content = b'complete locked bytes'
        counts = {}
        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *_): pass
            def do_GET(self):
                counts[self.path] = counts.get(self.path, 0) + 1
                if self.path == '/redirect':
                    self.send_response(302)
                    self.send_header('Location', 'https://evil.example/')
                    self.end_headers()
                    return
                if self.path == '/missing':
                    self.send_error(404)
                    return
                self.send_response(200)
                self.send_header('Content-Length', str(len(content)))
                self.end_headers()
                try:
                    if self.path == '/slow' and counts[self.path] == 1:
                        for byte in content:
                            self.wfile.write(bytes([byte]))
                            self.wfile.flush()
                            time.sleep(.2)
                    else:
                        self.wfile.write(content[:3] if self.path == '/partial' and counts[self.path] == 1 else content)
                except (BrokenPipeError, ssl.SSLError): pass
        server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(self.root / 'cert.pem', self.root / 'key.pem')
        server.socket = context.wrap_socket(server.socket, server_side=True)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        popen = subprocess.Popen
        def trusted(argv, **kwargs):
            # Test-only CA injection; production Curl has no custom-CA argument or environment.
            argv = list(argv)
            if '--speed-time' in argv:
                argv[argv.index('--speed-time') + 1] = '1'  # Shorten only the test observation window.
            return popen(argv + ['--cacert', str(self.root / 'cert.pem')], **kwargs)
        try:
            origin = f'https://localhost:{server.server_port}/'
            for route in ('complete', 'partial', 'slow', 'redirect', 'missing', 'untrusted', 'hostname'):
                with self.subTest(route=route), tempfile.TemporaryDirectory() as tmp, redirect_stdout(io.StringIO()) as log:
                    actual = origin if route != 'hostname' else origin.replace('localhost', '127.0.0.1')
                    client = packages.Curl(time.monotonic() + 15, threading.Event(), packages.Events())
                    target = Path(tmp) / 'package.deb'
                    with patch.object(packages, 'BASE', actual), patch.object(packages.subprocess, 'Popen', side_effect=popen if route == 'untrusted' else trusted), patch.object(client.stopped, 'wait', return_value=False):
                        client.version()
                        if route in ('complete', 'partial', 'slow'):
                            client.fetch(actual + route, target, 1024, hashlib.sha256(content).hexdigest())
                            self.assertEqual(target.read_bytes(), content)
                        else:
                            with self.assertRaises(RuntimeError): client.fetch(actual + route, target, 1024)
                            self.assertFalse(target.exists())
                    attempts = [json.loads(line) for line in log.getvalue().splitlines() if json.loads(line)['event'] == 'attempt']
                    self.assertEqual(len(attempts), 2 if route in ('partial', 'slow') else 1)
                    if route in ('untrusted', 'hostname'): self.assertEqual(attempts[0]['code'], 60)
                    if route == 'slow': self.assertEqual(attempts[0]['code'], 28)
            self.assertEqual(counts.get('/partial'), 2)
            self.assertNotIn('/untrusted', counts)
            self.assertNotIn('/hostname', counts)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=3)


if __name__ == '__main__':
    unittest.main()
