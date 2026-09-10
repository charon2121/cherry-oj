import importlib.util
from pathlib import Path
import tempfile
import unittest
import os

spec = importlib.util.spec_from_file_location('rootfs_build', Path(__file__).with_name('build.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SealTests(unittest.TestCase):
    def test_manifest_and_permissions(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'program').write_bytes(b'trusted binary')
            os.chmod(root / 'program', 0o6777)
            os.link(root / 'program', root / 'other')
            os.symlink('program', root / 'alias')
            m = module.seal(root, 'test fixture')
            entries = {e['Path']: e for e in m['Entries']}
            self.assertEqual(entries['program']['Mode'], 0o755)
            self.assertEqual(entries['.sandbox']['Mode'], 0o700)
            self.assertEqual(entries['alias']['Link'], 'program')
            self.assertEqual(entries['program']['SHA256'], module.digest(root / 'program'))
            self.assertEqual((root / 'program').stat().st_nlink, 1)
            self.assertEqual((root / 'other').stat().st_nlink, 1)

    def test_reject_special_file_and_mount_symlink(self):
        for special in ('fifo', 'mount'):
            with self.subTest(special=special), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                if special == 'fifo':
                    os.mkfifo(root / 'fifo')
                else:
                    os.symlink('/tmp', root / 'work')
                with self.assertRaises(ValueError):
                    module.seal(root, 'fixture')

    def test_usr_merged_layout_is_explicit_and_manifested(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            (root/'usr/lib64').mkdir(parents=True)
            module.apply_layout(root,None)
            self.assertFalse((root/'lib64').exists())
            module.apply_layout(root,'usr-merged')
            self.assertEqual(os.readlink(root/'lib64'),'usr/lib64')
            manifest=module.seal(root,'fixture')
            entry=next(e for e in manifest['Entries'] if e['Path']=='lib64')
            self.assertEqual(entry['Link'],'usr/lib64')

    def test_usr_merged_rejects_conflicts(self):
        for conflict in ('directory','link','unknown'):
            with self.subTest(conflict=conflict),tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);(root/'usr/lib').mkdir(parents=True)
                if conflict=='directory':(root/'lib').mkdir()
                if conflict=='link':(root/'lib').symlink_to('/lib')
                with self.assertRaises(ValueError):
                    module.apply_layout(root,'unknown' if conflict=='unknown' else 'usr-merged')
