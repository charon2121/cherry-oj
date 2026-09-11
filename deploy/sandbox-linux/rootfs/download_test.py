import unittest
import hashlib
import io
import json
import lzma
from contextlib import redirect_stdout
from pathlib import Path
import tempfile
import time
import urllib.error
from unittest.mock import ANY, Mock, patch
import download as module
from download import archive_path


class ArchiveLocationTests(unittest.TestCase):
    def test_epoch_in_local_apt_name_is_not_in_pool_filename(self):
        package=dict(package='cpp', version='4:13.2.0-7ubuntu1', architecture='amd64')
        path=archive_path(package,'pool/main/g/gcc-defaults/cpp_13.2.0-7ubuntu1_amd64.deb')
        self.assertEqual(str(path),'pool/main/g/gcc-defaults/cpp_13.2.0-7ubuntu1_amd64.deb')

    def test_rejects_archive_escape(self):
        package=dict(package='cpp',version='1',architecture='amd64')
        for path in ('/etc/passwd','pool/../../bad/file.deb'):
            with self.assertRaises(ValueError):
                archive_path(package,path)

    def fixture(self, root, content=b'locked-package'):
        lock = root / 'lock.json'
        package = dict(package='cpp', version='1', architecture='amd64', file='cpp_1_amd64.deb',
                       sha256=hashlib.sha256(content).hexdigest())
        lock.write_text(json.dumps(dict(packages=[package])))
        return lock, package

    def test_default_download_keeps_urlopen_and_opt_in_keeps_identical_bytes(self):
        for enabled in (False, True):
            with self.subTest(enabled=enabled), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                lock, package = self.fixture(root)
                standard, explicit = Mock(return_value=io.BytesIO(b'locked-package')), Mock(return_value=io.BytesIO(b'locked-package'))
                with patch.object(module, 'locations', return_value={'cpp': 'pool/main/c/cpp/current.deb'}), patch.object(
                        module.urllib.request, 'urlopen', standard), patch('transport.source_urlopen', return_value=explicit) as build, redirect_stdout(io.StringIO()) as log:
                    module.download(lock, root / 'packages', address_failover=enabled)
                used, unused = (explicit, standard) if enabled else (standard, explicit)
                used.assert_called_once_with(module.BASE + 'pool/main/c/cpp/cpp_1_amd64.deb', timeout=30,
                                             **({'observer': ANY} if enabled else {}))
                unused.assert_not_called()
                self.assertEqual(build.call_count, int(enabled))
                self.assertEqual((root / 'packages' / package['file']).read_bytes(), b'locked-package')
                if enabled:
                    events = [json.loads(line) for line in log.getvalue().splitlines() if line.startswith('{')]
                    self.assertEqual(events[-1]['stage'], 'verified')
                    self.assertEqual(events[-1]['readBytes'], len(b'locked-package'))
                    self.assertEqual({event['requestId'] for event in events}, {3})
                else:
                    self.assertEqual(log.getvalue(), 'verified ' + package['file'] + '\n')

    def test_opt_in_http_body_and_hash_failures_are_not_retried(self):
        for kind in ('http', 'body', 'hash', 'oversize'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                lock, _ = self.fixture(root)
                response = io.BytesIO(b'wrong-package')
                explicit = Mock(return_value=response)
                expected = ValueError
                if kind == 'http':
                    explicit.side_effect = urllib.error.HTTPError(module.BASE, 404, 'missing', {}, None)
                    expected = urllib.error.HTTPError
                elif kind in ('body', 'oversize'):
                    response = Mock()
                    response.__enter__ = Mock(return_value=response)
                    response.__exit__ = Mock(return_value=False)
                    explicit.return_value = response
                    if kind == 'body':
                        response.read.side_effect = TimeoutError('body stalled')
                        expected = TimeoutError
                    else:
                        class Oversize(bytes):
                            def __len__(self): return (100 << 20) + 1
                        response.read.return_value = Oversize(b'x')
                with patch.object(module, 'locations', return_value={'cpp': 'pool/main/c/cpp/current.deb'}), patch(
                        'transport.source_urlopen', return_value=explicit):
                    with self.assertRaises(expected):
                        module.download(lock, root / 'packages', address_failover=True)
                self.assertEqual(explicit.call_count, 1)

    def test_resume_rejects_symlink_wrong_hash_and_unsafe_name_before_download(self):
        for kind in ('symlink', 'hash', 'name'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                lock, package = self.fixture(root)
                output = root / 'packages'
                output.mkdir()
                target = output / package['file']
                if kind == 'symlink': target.symlink_to(root / 'outside')
                elif kind == 'hash': target.write_bytes(b'wrong')
                else:
                    package['file'] = '../outside'
                    lock.write_text(json.dumps(dict(packages=[package])))
                explicit = Mock()
                with patch.object(module, 'locations', return_value={'cpp': 'pool/main/c/cpp/current.deb'}), patch(
                        'transport.source_urlopen', return_value=explicit):
                    with self.assertRaises(ValueError):
                        module.download(lock, output, resume=True, address_failover=True)
                explicit.assert_not_called()

    def test_diagnostics_identify_body_timeout_without_leaking_error_or_retrying(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            lock, package = self.fixture(root)
            response = Mock()
            response.__enter__ = Mock(return_value=response)
            response.__exit__ = Mock(return_value=False)
            response.status = 200
            response.read.side_effect = [b'partial', TimeoutError('secret-response')]
            explicit = Mock(return_value=response)
            with patch.object(module, 'locations', return_value={'cpp': 'pool/main/c/cpp/current.deb'}), patch(
                    'transport.source_urlopen', return_value=explicit), redirect_stdout(io.StringIO()) as log:
                with self.assertRaises(TimeoutError):
                    module.download(lock, root / 'packages', address_failover=True)
            events = [json.loads(line) for line in log.getvalue().splitlines() if line.startswith('{')]
            self.assertTrue(events, 'missing request stage diagnostics')
            self.assertEqual(events[-1]['stage'], 'body')
            self.assertEqual(events[-1]['outcome'], 'TIMEOUT')
            self.assertEqual(events[-1]['readBytes'], 7)
            self.assertEqual(events[-1]['name'], package['file'])
            self.assertNotIn('secret-response', log.getvalue())
            explicit.assert_called_once()

    def test_parallel_completions_keep_legacy_and_json_records_on_separate_lines(self):
        class YieldingOutput(io.StringIO):
            def write(self, text):
                size = super().write(text)
                time.sleep(.001)  # Exercise print's separate text/newline writes.
                return size
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            lock, package = self.fixture(root)
            packages = [dict(package, package='pkg' + str(i), file='pkg' + str(i) + '_1_amd64.deb') for i in range(8)]
            lock.write_text(json.dumps(dict(packages=packages)))
            paths = {p['package']: 'pool/main/p/pkg/current.deb' for p in packages}
            with patch.object(module, 'locations', return_value=paths), patch('transport.source_urlopen', return_value=Mock(
                    side_effect=lambda *_a, **_k: io.BytesIO(b'locked-package'))), redirect_stdout(YieldingOutput()) as log:
                module.download(lock, root / 'packages', address_failover=True)
            lines = log.getvalue().splitlines()
            legacy = [line for line in lines if line.startswith('verified ')]
            self.assertEqual(legacy, ['verified ' + p['file'] for p in packages])
            events = [json.loads(line) for line in lines if not line.startswith('verified ')]
            completed = [event for event in events if event['stage'] == 'verified']
            self.assertEqual({event['requestId'] for event in completed}, set(range(3, 11)))
            self.assertEqual(len(completed), 8)

    def test_index_diagnostics_include_parsing_and_do_not_claim_invalid_index_ready(self):
        from diagnostics import Diagnostics
        for valid in (True, False):
            with self.subTest(valid=valid), redirect_stdout(io.StringIO()) as log:
                content = lzma.compress(b'Package: cpp\nFilename: pool/main/c/cpp/current.deb\n\n') if valid else b'bad-index'
                open_url = Mock(side_effect=lambda *_a, **_k: io.BytesIO(content))
                if valid:
                    self.assertEqual(module.locations(open_url, Diagnostics(True)), {'cpp': 'pool/main/c/cpp/current.deb'})
                else:
                    with self.assertRaises(lzma.LZMAError): module.locations(open_url, Diagnostics(True))
                events = [json.loads(line) for line in log.getvalue().splitlines()]
                if valid:
                    self.assertEqual([event['requestId'] for event in events if event['stage'] == 'index_ready'], [1, 2])
                else:
                    self.assertEqual(events[-1]['stage'], 'parse')
                    self.assertEqual(events[-1]['outcome'], 'FAILED')
                    self.assertFalse(any(event['stage'] == 'index_ready' for event in events))


if __name__=='__main__':
    unittest.main()
