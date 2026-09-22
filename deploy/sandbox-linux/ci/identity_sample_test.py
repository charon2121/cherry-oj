#!/usr/bin/env python3
"""验证身份采样不会把不可观测的权限、I/O 故障误报为通过。"""

from __future__ import annotations

import errno
import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch


SOURCE = Path(__file__).resolve().parents[1] / 'tests' / 'identity_sample.py'
SPEC = importlib.util.spec_from_file_location('identity_sample', SOURCE)
assert SPEC is not None and SPEC.loader is not None
SAMPLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SAMPLER)


class IdentitySampleTest(unittest.TestCase):
    def test_disappearance_and_observation_errors(self) -> None:
        group = Path('/owned-test-group')
        for site in ('cgroup.procs', 'status'):
            for number in (errno.ENOENT, errno.ENODEV, errno.EACCES, errno.EIO, errno.EMFILE):
                with self.subTest(site=site, errno=number):
                    failed = False

                    def read(path: Path) -> str:
                        if failed and path.name == site:
                            raise OSError(number, 'injected observation failure')
                        if path.name == 'cgroup.procs':
                            return '61002 61003 61004 61005'
                        return 'Uid:\t' + path.parent.name

                    with patch.object(Path, 'read_text', read):
                        # 已有完整样本也不能覆盖后续观察故障。
                        self.assertEqual(SAMPLER.sample([group]), ({61002, 61004}, {61003, 61005}))
                        failed = True
                        allowed = number == errno.ENOENT or (site == 'cgroup.procs' and number == errno.ENODEV)
                        if allowed:
                            self.assertEqual(SAMPLER.sample([group]), (set(), set()))
                        else:
                            with self.assertRaises(OSError) as caught:
                                SAMPLER.sample([group])
                            self.assertEqual(caught.exception.errno, number)


if __name__ == '__main__':
    unittest.main()
