from pathlib import Path
import tempfile
import unittest
from unittest import mock

import owned


class OwnershipTests(unittest.TestCase):
    def test_privileged_entry_rejects_non_github_hosts(self):
        with mock.patch.dict('os.environ', {}, clear=True):
            with self.assertRaises(RuntimeError):
                owned.github_vm()

    def test_existing_fixture_is_never_adopted(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            marker = path / 'user-data'
            marker.write_text('preserve')
            with mock.patch.object(owned, 'github_vm', return_value='1-1'), \
                    mock.patch.object(owned, 'BASE', path), \
                    mock.patch.object(Path, 'read_text', return_value='systemd'):
                with self.assertRaises(RuntimeError):
                    owned.preflight()
            self.assertEqual(marker.read_text(), 'preserve')

    def test_stop_cannot_target_an_unregistered_unit(self):
        resources = owned.Owned.__new__(owned.Owned)
        resources.data = {'units': ['cherry-sandbox-test-work048-owned-1-1']}
        with mock.patch.object(owned.subprocess, 'run') as run:
            with self.assertRaises(ValueError):
                resources.stop('user-database.service')
            run.assert_not_called()

    def test_residual_resources_prevent_file_removal(self):
        with tempfile.TemporaryDirectory() as directory:
            resources = owned.Owned.__new__(owned.Owned)
            resources.output = Path(directory)
            resources.data = {'units': []}
            with mock.patch.object(owned, 'snapshot', return_value={'tasks': [{'pid': 123}], 'mounts': [], 'cgroups': []}), \
                    mock.patch.object(owned.time, 'monotonic', side_effect=[0, 4]), \
                    mock.patch.object(owned.shutil, 'rmtree') as remove:
                with self.assertRaises(RuntimeError):
                    resources.cleanup()
                remove.assert_not_called()
