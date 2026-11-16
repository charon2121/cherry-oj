#!/usr/bin/env python3
"""验证外部进程期限、备份发布条件及健康检查失败边界。"""

from __future__ import annotations

import gzip
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import host
from artifacts import digest
from fixtures import MYSQL, PORTS
from host import Host, Mysql


class HostTests(unittest.TestCase):
    def test_mysql_identifier_and_config_arguments_cannot_inject_commands_or_sql(self) -> None:
        for value in [('--privileged', '/run/client.cnf', 'fixture_'),
                      ('mysql', '../../client.cnf', 'fixture_'),
                      ('mysql', '/run/../client.cnf', 'fixture_'),
                      ('mysql', '/run/client.cnf', 'a;DROP DATABASE x;'),
                      ('mysql', False, 'fixture_')]:
            with self.subTest(value=value), self.assertRaises(ValueError): Mysql(*value)

    def test_command_timeout_terminates_its_process_group(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            tool = Host(MYSQL, PORTS, Path(temporary))
            with self.assertRaises(subprocess.TimeoutExpired):
                tool.command([sys.executable, '-c', 'import time;time.sleep(30)'], timeout=.05)
            self.assertEqual(len(list(Path(temporary).glob('*-command.log'))), 1)

    def test_recovery_keeps_existing_logs_instead_of_overwriting_them(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.assertEqual(Host(MYSQL, PORTS, root).command([sys.executable, '-c', 'print("first")']), 'first')
            self.assertEqual(Host(MYSQL, PORTS, root).command([sys.executable, '-c', 'print("second")']), 'second')
            self.assertEqual((root / '001-command.log').read_text().strip(), 'first')
            self.assertEqual((root / '002-command.log').read_text().strip(), 'second')

    def test_backup_only_publishes_a_complete_successful_dump(self) -> None:
        real_popen = subprocess.Popen
        for failure in (False, True):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                script = 'import sys;print("fixture dump");sys.exit(' + str(int(failure)) + ')'
                with patch.object(host.subprocess, 'Popen', side_effect=lambda *args, **kw: real_popen(
                        [sys.executable, '-c', script], **kw)):
                    if failure:
                        with self.assertRaises(RuntimeError): Host(MYSQL, PORTS, root).backup()
                        self.assertTrue((root / 'databases.sql.gz.partial').exists())
                        self.assertFalse((root / 'databases.sql.gz').exists())
                    else:
                        self.assertEqual(Host(MYSQL, PORTS, root).backup(), digest(root / 'databases.sql.gz'))
                        with gzip.open(root / 'databases.sql.gz', 'rt') as stream:
                            self.assertEqual(stream.read(), 'fixture dump\n')
                        self.assertFalse((root / 'databases.sql.gz.partial').exists())

    def test_stalled_backup_obeys_deadline_before_waiting_for_end_of_stream(self) -> None:
        real_popen = subprocess.Popen
        children = []

        def start(*args: object, **kwargs: object) -> subprocess.Popen:
            child = real_popen([sys.executable, '-c', 'import time;time.sleep(30)'], **kwargs)
            children.append(child)
            return child

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with patch.object(host.subprocess, 'Popen', side_effect=start), \
                    patch.object(host.time, 'monotonic', side_effect=[0, 181]), self.assertRaises(TimeoutError):
                Host(MYSQL, PORTS, root).backup()
            self.assertIsNotNone(children[0].poll())
            self.assertFalse((root / 'databases.sql.gz').exists())

    def test_failed_unit_is_not_polled_until_timeout(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            tool = Host(MYSQL, PORTS, Path(temporary))
            with patch.object(tool, 'unit', return_value={'ActiveState': 'failed'}), \
                    patch.object(tool, 'get') as get, self.assertRaisesRegex(RuntimeError, '已经退出'):
                tool.health('user')
            get.assert_not_called()

    def test_healthy_loopback_service_does_not_hide_a_second_public_listener(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            tool = Host(MYSQL, PORTS, Path(temporary))
            lines = '\n'.join(f'LISTEN 0 128 127.0.0.1:{port} 0.0.0.0:*' for port in [*PORTS.values(), 18080])
            with patch.object(tool, 'command', return_value=lines): tool.listeners()
            with patch.object(tool, 'command', return_value=lines.replace('127.0.0.1:8081', '[::ffff:127.0.0.1]:8081')):
                tool.listeners()
            for address in ('[::]', '0.0.0.0', '[::ffff:0.0.0.0]', '192.0.2.1'):
                with self.subTest(address=address), patch.object(tool, 'command',
                    return_value=lines + f'\nLISTEN 0 128 {address}:8080 [::]:*'), \
                        self.assertRaisesRegex(ValueError, '回环'): tool.listeners()

    def test_running_service_must_start_from_current_and_keep_its_service_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            tool = Host(MYSQL, PORTS, Path(temporary))

            def properties(argv: list[str], timeout: int = 45) -> str:
                service = argv[2].removeprefix('cherry-app-').removesuffix('.service')
                return (f'User=cherry-app-{service}\nGroup=cherry-app-{service}\n'
                        f'WorkingDirectory=/var/lib/cherry-app/{service}\n'
                        f'ExecStart={{ path=/usr/bin/java ; argv[]=/usr/bin/java -jar /opt/cherry-app/current/jars/{service}-service.jar ; }}')

            with patch.object(tool, 'command', side_effect=properties): tool.app_units()
            for value in ('User=root', 'Group=root', 'WorkingDirectory=/tmp', 'ExecStart=java -jar /old/service.jar'):
                with self.subTest(value=value), patch.object(tool, 'command', return_value=value), \
                        self.assertRaises(ValueError): tool.app_units()

    def test_failed_unit_with_no_process_is_stopped_but_live_or_deactivating_units_are_not(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            tool = Host(MYSQL, PORTS, Path(temporary))
            with patch.object(tool, 'command'), patch.object(tool, 'unit', return_value={
                    'ActiveState': 'failed', 'MainPID': '0'}):
                tool.stop()
            for state in ({'ActiveState': 'inactive', 'MainPID': '123'},
                          {'ActiveState': 'deactivating', 'MainPID': '0'},
                          {'ActiveState': 'active', 'MainPID': '0'}):
                with self.subTest(state=state), patch.object(tool, 'command'), \
                        patch.object(tool, 'unit', return_value=state), self.assertRaises(RuntimeError):
                    tool.stop()


if __name__ == '__main__':
    unittest.main()
