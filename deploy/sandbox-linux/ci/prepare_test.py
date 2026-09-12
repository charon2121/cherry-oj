"""CI selects the reviewed download option and its explicitly approved deadline."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import prepare


class PrepareTests(unittest.TestCase):
    def test_ci_selects_failover_with_approved_download_deadline(self):
        class StopAfterDownload(Exception): pass
        def run(argv, log, seconds, **_kwargs):
            if Path(log).name == 'download.log':
                self.assertIn('--address-failover', argv)
                self.assertEqual(seconds, 600)
                raise StopAfterDownload()
        with tempfile.TemporaryDirectory() as tmp, patch.object(prepare.platform, 'system', return_value='Linux'), patch.object(
                prepare.platform, 'machine', return_value='x86_64'), patch.object(prepare, 'install_signal_handlers'), patch.object(
                prepare, 'linux_units'), patch.object(prepare, 'run', side_effect=run), patch(
                'sys.argv', ['prepare.py', '--output', str(Path(tmp) / 'build')]):
            with self.assertRaises(StopAfterDownload): prepare.main()


    def test_supplied_packages_are_verified_before_rootfs_without_downloading(self):
        class StopAtRootfs(Exception): pass
        calls = []
        def run(argv, log, seconds, **_kwargs):
            calls.append((argv, Path(log).name))
            if Path(log).name == 'rootfs.log': raise StopAtRootfs()
        with tempfile.TemporaryDirectory() as tmp, patch.object(prepare.platform, 'system', return_value='Linux'), patch.object(
                prepare.platform, 'machine', return_value='x86_64'), patch.object(prepare, 'install_signal_handlers'), patch.object(
                prepare, 'linux_units'), patch.object(prepare, 'run', side_effect=run), patch(
                'sys.argv', ['prepare.py', '--output', str(Path(tmp) / 'build'), '--packages', str(Path(tmp) / 'shared')]):
            with self.assertRaises(StopAtRootfs): prepare.main()
        self.assertNotIn('download.log', [name for _, name in calls])
        self.assertEqual([name for _, name in calls][-2:], ['packages.log', 'rootfs.log'])
        self.assertIn('--source', calls[-2][0])


if __name__ == '__main__':
    unittest.main()
