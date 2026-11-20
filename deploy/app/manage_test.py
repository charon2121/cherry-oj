#!/usr/bin/env python3
"""通过故障注入验证应用升级、回滚与中断恢复的边界。"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from artifacts import digest
from fixtures import MYSQL, Scenario, bundle, fake_host, installation, legacy
from manage import execute, recover
from state import read_json, save


class ManageTests(unittest.TestCase):
    def test_upgrade_adopts_legacy_without_editing_it_and_backups_precede_switch(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            store = installation(root)
            original = (store.home / 'releases/old/release.json').read_bytes()
            package, expected, new = bundle(root)
            scenario = Scenario()
            operation = execute(store, MYSQL, 'UPGRADE', package, expected, fake_host(scenario))
            self.assertEqual(operation.state, 'HEALTHY')
            self.assertEqual(store.current(), new)
            self.assertEqual((store.home / 'releases/old/release.json').read_bytes(), original)
            self.assertEqual(store.receipt()['previous'], 'old')
            self.assertNotIn('judgeRelease', store.receipt())
            self.assertTrue(scenario.running)
            self.assertLess(scenario.events.index('backup'), scenario.events.index('start'))
            backup = store.operations / operation.operationId / 'databases.sql.gz'
            self.assertEqual(digest(backup), operation.databaseBackupSha256)
            self.assertEqual(store.pending(), [])

    def test_rollback_swaps_current_and_previous_without_removing_releases(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            store = installation(root)
            package, expected, new = bundle(root)
            host = fake_host(Scenario())
            execute(store, MYSQL, 'UPGRADE', package, expected, host)
            execute(store, MYSQL, 'ROLLBACK', host_type=host)
            self.assertEqual(store.current(), 'old')
            self.assertEqual(store.receipt()['previous'], new)
            execute(store, MYSQL, 'ROLLBACK', host_type=host)
            self.assertEqual(store.current(), new)
            self.assertTrue((store.home / 'releases/old').is_dir())

    def test_rollback_accepts_legacy_previous_path_and_keeps_its_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = installation(Path(temporary))
            target = store.home / 'releases/older'
            legacy(target)
            manifest = (target / 'release.json').read_bytes()
            receipt = store.receipt()
            receipt['previous'] = 'releases/older'
            save(store.home / 'deployment.json', receipt)

            execute(store, MYSQL, 'ROLLBACK', host_type=fake_host(Scenario()))

            self.assertEqual(store.current(), 'older')
            self.assertEqual(store.receipt()['previous'], 'old')
            self.assertEqual(store.receipt()['version'], 1)
            self.assertEqual((target / 'release.json').read_bytes(), manifest)

    def test_backup_and_start_failures_restore_the_original_receipt_and_processes(self) -> None:
        for kind in ('backup', 'start', 'stop'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                store = installation(root)
                receipt = (store.home / 'deployment.json').read_bytes()
                package, expected, _ = bundle(root)
                scenario = Scenario(fail_backup=kind == 'backup', fail_start=int(kind == 'start'), fail_stop=int(kind == 'stop'))
                with self.assertRaisesRegex(RuntimeError, '已恢复 old'):
                    execute(store, MYSQL, 'UPGRADE', package, expected, fake_host(scenario))
                self.assertEqual(store.current(), 'old')
                self.assertEqual((store.home / 'deployment.json').read_bytes(), receipt)
                self.assertTrue(scenario.running)
                self.assertFalse(store.pending())
                if kind == 'backup': self.assertEqual(scenario.starts, 1)

    def test_startup_migration_failure_leaves_services_stopped_and_refuses_another_operation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            store = installation(root)
            package, expected, new = bundle(root)
            scenario = Scenario(fail_start=1, migrate_on_start=True)
            host = fake_host(scenario)
            with self.assertRaisesRegex(RuntimeError, '人工恢复'):
                execute(store, MYSQL, 'UPGRADE', package, expected, host)
            self.assertFalse(scenario.running)
            self.assertEqual(store.current(), new)
            self.assertEqual(store.receipt()['status'], 'RECOVERY_REQUIRED')
            self.assertEqual(store.pending()[0].state, 'RECOVERY_REQUIRED')
            self.assertNotEqual(store.pending()[0].schemaAfter, store.pending()[0].schemaBefore)
            with self.assertRaisesRegex(ValueError, 'recover'):
                execute(store, MYSQL, 'ROLLBACK', host_type=host)
            with self.assertRaisesRegex(RuntimeError, '无法自动恢复'):
                recover(store, MYSQL, host)
            self.assertFalse(scenario.running)

    def test_failure_restoring_old_application_stops_partially_started_services(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            store = installation(root)
            package, expected, _ = bundle(root)
            scenario = Scenario(fail_start=2)
            with self.assertRaisesRegex(RuntimeError, '人工恢复'):
                execute(store, MYSQL, 'UPGRADE', package, expected, fake_host(scenario))
            self.assertFalse(scenario.running)
            self.assertEqual(store.pending()[0].state, 'RECOVERY_REQUIRED')

    def test_unreadable_history_after_failed_start_is_not_treated_as_unchanged(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            store = installation(root)
            package, expected, new = bundle(root)
            scenario = Scenario(fail_start=1, fail_history_after_start=True)
            with self.assertRaisesRegex(RuntimeError, '人工恢复'):
                execute(store, MYSQL, 'UPGRADE', package, expected, fake_host(scenario))
            self.assertEqual(store.current(), new)
            self.assertFalse(scenario.running)
            self.assertIsNone(store.pending()[0].schemaAfter)
            self.assertEqual(store.pending()[0].state, 'RECOVERY_REQUIRED')

    def test_recovery_checks_real_pointer_even_when_switch_log_was_not_flushed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            store = installation(root)
            package, expected, _ = bundle(root)
            scenario = Scenario()
            host = fake_host(scenario)
            operation = execute(store, MYSQL, 'UPGRADE', package, expected, host)
            operation.state = 'BACKED_UP'
            store.persist(operation)
            restored = recover(store, MYSQL, host)
            self.assertEqual(restored.state, 'ROLLED_BACK')
            self.assertEqual(store.current(), 'old')
            self.assertTrue(scenario.running)

    def test_recovery_before_switch_can_restart_old_application_without_schema_snapshot(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            store = installation(root)
            package, expected, _ = bundle(root)
            scenario = Scenario()
            host = fake_host(scenario)
            operation = execute(store, MYSQL, 'UPGRADE', package, expected, host)
            directory = store.operations / operation.operationId
            store.switch('old')
            (store.home / 'deployment.json').write_bytes((directory / 'deployment.before.json').read_bytes())
            operation.state = 'STOPPED'
            operation.schemaBefore = None
            operation.schemaAfter = None
            store.persist(operation)
            self.assertEqual(recover(store, MYSQL, host).state, 'ROLLED_BACK')
            self.assertEqual(store.current(), 'old')

    def test_corrupt_package_and_reused_release_do_not_stop_running_services(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            store = installation(root)
            package, expected, _ = bundle(root)
            scenario = Scenario()
            host = fake_host(scenario)
            with self.assertRaises(ValueError): execute(store, MYSQL, 'UPGRADE', package, '0' * 64, host)
            self.assertEqual(scenario.stops, 0)
            execute(store, MYSQL, 'UPGRADE', package, expected, host)
            stops = scenario.stops
            with self.assertRaisesRegex(ValueError, '已使用'):
                execute(store, MYSQL, 'UPGRADE', package, expected, host)
            self.assertEqual(scenario.stops, stops)

    def test_rollback_with_different_migrations_is_rejected_before_stopping(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            store = installation(root)
            legacy(store.home / 'releases/older', migration='other schema')
            receipt = store.receipt()
            receipt['previous'] = 'releases/older'
            save(store.home / 'deployment.json', receipt)
            scenario = Scenario()
            with self.assertRaisesRegex(ValueError, '数据库迁移不同'):
                execute(store, MYSQL, 'ROLLBACK', host_type=fake_host(scenario))
            self.assertEqual(scenario.stops, 0)
            self.assertEqual(store.current(), 'old')

    def test_concurrent_operation_cannot_take_the_same_lock(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = installation(Path(temporary))
            with store.lock(), self.assertRaisesRegex(RuntimeError, '正在执行'):
                with store.lock():
                    self.fail('second lock was acquired')

    def test_unknown_operation_version_and_external_current_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            store = installation(root)
            package, expected, _ = bundle(root)
            operation = execute(store, MYSQL, 'UPGRADE', package, expected, fake_host(Scenario()))
            record = store.operations / operation.operationId / 'operation.json'
            document = read_json(record)
            document['version'] = 2
            save(record, document)
            with self.assertRaises(ValueError): store.pending()
            (store.home / 'current').unlink()
            (store.home / 'current').symlink_to(root)
            with self.assertRaises(ValueError): store.current()


if __name__ == '__main__':
    unittest.main()
