#!/usr/bin/env python3
"""业务采样只容忍资源消失，其他读取失败仍必须阻止通过。"""

from __future__ import annotations

import errno
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from business_observer import Observer


class BusinessObserverTest(unittest.TestCase):
    def fixture(self, root: Path) -> tuple[Observer, Path]:
        (root / 'observed-case').write_text('cpu')
        group = root / 'jobs' / 'execution'
        group.mkdir(parents=True)
        (group / 'cgroup.procs').write_text('999999999')
        observer = Observer(root, root)
        observer.stop = Mock()
        observer.stop.wait.side_effect = [False, True]
        return observer, group

    def test_disappearing_cgroup_can_be_skipped_during_discovery(self) -> None:
        read_text = Path.read_text
        for number in (errno.ENOENT, errno.ENODEV, errno.EACCES, errno.EIO, errno.EMFILE):
            with self.subTest(errno=number), tempfile.TemporaryDirectory() as temp:
                observer, group = self.fixture(Path(temp))

                def read(path: Path) -> str:
                    if path == group / 'cgroup.procs':
                        raise OSError(number, 'private diagnostic must not escape')
                    return read_text(path)

                with patch('business_observer.JOBS', group.parent), patch.object(Path, 'read_text', read):
                    observer.watch()
                self.assertEqual(observer.failure is None, number in (errno.ENOENT, errno.ENODEV))

    def test_counter_errors_are_not_hidden_by_group_disappearance(self) -> None:
        # 先有一次有效采样，随后 cgroup 消失；EIO 等不能借“目录不在了”被吞掉。
        for number in (errno.ENOENT, errno.ENODEV, errno.EACCES, errno.EIO, errno.EMFILE):
            with self.subTest(errno=number), tempfile.TemporaryDirectory() as temp:
                observer, group = self.fixture(Path(temp))
                observer.stop.wait.side_effect = [False, False, True]
                original_read = Path.read_text
                read_calls = 0

                def read(path: Path) -> str:
                    if path.name == 'status':
                        return 'Uid:\t61002\t61002\t61002\t61002\n'
                    return original_read(path)

                def counter(fd: int) -> str:
                    nonlocal read_calls
                    read_calls += 1
                    if read_calls > 2:
                        raise OSError(number, 'private diagnostic must not escape')
                    return 'usage_usec 1000000\n' if fd == 31 else 'oom_kill 1\n'

                exists = Path.exists
                def present(path: Path) -> bool:
                    if path == Path('/proc/999999999') or path == group:
                        return read_calls <= 2
                    return exists(path)

                with patch('business_observer.JOBS', group.parent), \
                     patch.object(Path, 'read_text', read), \
                     patch.object(Path, 'read_bytes', return_value=b'Main\0'), \
                     patch.object(Path, 'exists', present), \
                     patch('business_observer.os.open', side_effect=[31, 32]), \
                     patch('business_observer.os.close') as close, \
                     patch('business_observer.read_fd', side_effect=counter):
                    observer.watch()
                self.assertEqual(observer.failure is None, number in (errno.ENOENT, errno.ENODEV))
                self.assertEqual(len(observer.records), 1)
                self.assertEqual(observer.records[0]['cpu']['usage_usec'], 1000000)
                self.assertEqual(observer.records[0]['memoryEvents']['oom_kill'], 1)
                self.assertEqual([c.args[0] for c in close.call_args_list], [31, 32])

    def test_diagnostic_preserves_errno_and_operation_without_private_text(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            observer, group = self.fixture(root)
            original_read = Path.read_text

            def read(path: Path) -> str:
                if path == group / 'cgroup.procs':
                    raise OSError(errno.EIO, 'private-token-and-path')
                return original_read(path)

            with patch('business_observer.JOBS', group.parent), patch.object(Path, 'read_text', read):
                observer.watch()
            observer.thread = Mock()
            observer.thread.is_alive.return_value = False
            with self.assertRaisesRegex(RuntimeError, 'execution observation failed'):
                observer.__exit__()
            raw = (root / 'execution-observations.json').read_text()
            value = json.loads(raw)
            self.assertEqual(value['errorErrno'], errno.EIO)
            self.assertEqual(value['errorOperation'], 'cgroup-procs')
            self.assertNotIn('private-token-and-path', raw)

    def test_process_and_counter_open_errors_use_their_own_disappearance_rules(self) -> None:
        for site in ('status', 'cmdline', 'cpu.stat', 'memory.events'):
            for number in (errno.ENOENT, errno.ENODEV, errno.ESRCH, errno.EACCES, errno.EIO, errno.EMFILE):
                with self.subTest(site=site, errno=number), tempfile.TemporaryDirectory() as temp:
                    observer, group = self.fixture(Path(temp))
                    original_read = Path.read_text

                    def read(path: Path) -> str:
                        if path.name == site:
                            raise OSError(number, 'injected')
                        if path.name == 'status':
                            return 'Uid:\t61002\t61002\t61002\t61002\n'
                        return original_read(path)

                    def argv(path: Path) -> bytes:
                        if site == 'cmdline':
                            raise OSError(number, 'injected')
                        return b'Main\0'

                    def open_counter(path: Path, flags: int) -> int:
                        if path.name == site:
                            raise OSError(number, 'injected')
                        return 31

                    with patch('business_observer.JOBS', group.parent), \
                         patch.object(Path, 'read_text', read), \
                         patch.object(Path, 'read_bytes', argv), \
                         patch('business_observer.os.open', side_effect=open_counter), \
                         patch('business_observer.os.close') as close:
                        observer.watch()
                    allowed = (errno.ENOENT, errno.ESRCH) if site in ('status', 'cmdline') else (errno.ENOENT, errno.ENODEV)
                    self.assertEqual(observer.failure is None, number in allowed)
                    self.assertEqual(observer.records, [])
                    self.assertEqual([c.args[0] for c in close.call_args_list], [31] if site == 'memory.events' else [])


if __name__ == '__main__':
    unittest.main()
