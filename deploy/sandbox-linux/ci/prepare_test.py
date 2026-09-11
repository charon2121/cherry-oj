"""CI selects the reviewed download option without extending its command budget."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import prepare


class PrepareTests(unittest.TestCase):
    def test_ci_selects_failover_with_original_command_deadline(self):
        class StopAfterDownload(Exception): pass
        def run(argv, log, seconds, **_kwargs):
            if Path(log).name == 'download.log':
                self.assertIn('--address-failover', argv)
                self.assertEqual(seconds, 240)
                raise StopAfterDownload()
        with tempfile.TemporaryDirectory() as tmp, patch.object(prepare.platform, 'system', return_value='Linux'), patch.object(
                prepare.platform, 'machine', return_value='x86_64'), patch.object(prepare, 'install_signal_handlers'), patch.object(
                prepare, 'linux_units'), patch.object(prepare, 'run', side_effect=run), patch(
                'sys.argv', ['prepare.py', '--output', str(Path(tmp) / 'build')]):
            with self.assertRaises(StopAfterDownload): prepare.main()


if __name__ == '__main__':
    unittest.main()
