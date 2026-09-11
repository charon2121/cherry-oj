import unittest
import hashlib
import io
import json
from pathlib import Path
import tempfile
import urllib.error
from unittest.mock import Mock, patch
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
                        module.urllib.request, 'urlopen', standard), patch('transport.source_urlopen', return_value=explicit) as build:
                    module.download(lock, root / 'packages', address_failover=enabled)
                used, unused = (explicit, standard) if enabled else (standard, explicit)
                used.assert_called_once_with(module.BASE + 'pool/main/c/cpp/cpp_1_amd64.deb', timeout=30)
                unused.assert_not_called()
                self.assertEqual(build.call_count, int(enabled))
                self.assertEqual((root / 'packages' / package['file']).read_bytes(), b'locked-package')

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


if __name__=='__main__':
    unittest.main()
