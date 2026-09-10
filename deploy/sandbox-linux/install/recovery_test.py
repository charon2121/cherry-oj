"""Restore refuses changed ownership or content and never overwrites existing units."""
from contextlib import ExitStack
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import manage


class RestoreTests(unittest.TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        root = Path(self.stack.enter_context(tempfile.TemporaryDirectory()))
        self.state = root / 'state'
        self.units = root / 'systemd'
        self.state.mkdir()
        self.units.mkdir()
        self.receipt = self.state / 'installation.json'
        self.commands = []
        for key, value in [('STATE', self.state), ('SYSTEMD', self.units), ('RECEIPT', self.receipt)]:
            self.stack.enter_context(patch.object(manage, key, value))
        # macOS tests use ordinary temporary directories; root path checks run on Linux.
        self.stack.enter_context(patch.object(manage, 'protected'))
        self.stack.enter_context(patch.object(manage, 'run', side_effect=self.command))
        self.account = self.stack.enter_context(patch.object(manage.pwd, 'getpwnam',
            side_effect=lambda name: SimpleNamespace(pw_uid=manage.ACCOUNTS[name], pw_gid=manage.ACCOUNTS[name])))
        self.stack.enter_context(patch.object(manage.grp, 'getgrnam',
            side_effect=lambda name: SimpleNamespace(gr_gid=manage.ACCOUNTS[name])))
        retained = self.state / 'data'
        retained.write_text('retained user data')
        records = {str(retained): manage.digest(retained)}
        for name in manage.UNITS:
            path = self.units / name
            path.write_text('original unit ' + name)
            records[str(path)] = manage.digest(path)
        self.original = dict(version=1, status='stopped', files=records)
        manage.write_json(self.receipt, self.original)
        manage.backup_units(self.original)
        for name in manage.UNITS:
            (self.units / name).unlink()
        self.original['status'] = 'uninstalled'
        manage.write_json(self.receipt, self.original)

    def command(self, *args):
        self.commands.append(args)
        if args[:2] == ('systemctl', 'show') and 'LoadState' in args:
            return 'LoadState=not-found\nActiveState=inactive\nDropInPaths=\n'
        return ''

    def assert_not_restored(self):
        self.assertFalse(any(self.units.iterdir()))
        self.assertEqual(json.loads(self.receipt.read_text())['status'], 'uninstalled')

    def test_restore_exact_units_preserving_data_without_start(self):
        manage.restore()
        for name in manage.UNITS:
            self.assertEqual(manage.digest(self.units / name), self.original['files'][str(self.units / name)])
        self.assertEqual((self.state / 'data').read_text(), 'retained user data')
        self.assertEqual(json.loads(self.receipt.read_text())['status'], 'installed')
        self.assertFalse(any('start' in c or 'enable' in c for c in self.commands))

    def test_changed_backup_refused_before_any_unit_written(self):
        (self.state / 'unit-backup' / manage.UNITS[-1]).write_text('changed')
        with self.assertRaises(ValueError):
            manage.restore()
        self.assert_not_restored()

    def test_existing_unit_never_overwritten(self):
        foreign = self.units / manage.UNITS[-1]
        foreign.write_text('foreign unit')
        with self.assertRaises(ValueError):
            manage.restore()
        self.assertEqual(foreign.read_text(), 'foreign unit')
        self.assertEqual(list(self.units.iterdir()), [foreign])

    def test_changed_account_refused(self):
        self.account.side_effect = lambda name: SimpleNamespace(pw_uid=123, pw_gid=123)
        with self.assertRaises(ValueError):
            manage.restore()
        self.assert_not_restored()

    def test_failed_unit_verification_rolls_back_only_created_files(self):
        def command(*args):
            if args[0] == 'systemd-analyze':
                raise OSError('invalid unit')
            return self.command(*args)
        with patch.object(manage, 'run', side_effect=command):
            with self.assertRaises(OSError):
                manage.restore()
        self.assert_not_restored()
        self.assertTrue((self.state / 'data').exists())

    def test_restored_installation_cannot_be_restored_twice(self):
        manage.restore()
        with self.assertRaises(ValueError):
            manage.restore()


if __name__ == '__main__':
    unittest.main()
