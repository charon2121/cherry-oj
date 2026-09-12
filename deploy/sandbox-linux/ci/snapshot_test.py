"""Exercise the real inspection functions without starting privileged children."""
import ast
import copy
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import Mock

from report import ROOT

SOURCE = ROOT / 'deploy/sandbox-linux/tests/inspect_threads.py'


class SnapshotTests(unittest.TestCase):
    def setUp(self):
        tree = ast.parse(SOURCE.read_text())
        self.functions = ast.Module(body=[n for n in tree.body if isinstance(n, ast.FunctionDef)], type_ignores=[])
        self.sample = ast.Module(body=next(n for n in tree.body if isinstance(n, ast.Try)).body, type_ignores=[])

    def facts(self):
        rows = [(str(index), str(index), dict(Uid=' '.join([uid]*4), Gid=' '.join([uid]*4),
                 NoNewPrivs='1', Seccomp='2', CapEff='0')) for index, uid in enumerate(('61002', '61003'), 1)]
        namespaces = {pid: {name: ('host-' if name == 'user' else 'child-') + name
                      for name in ('mnt', 'pid', 'net', 'ipc', 'uts', 'cgroup', 'user')} for pid, _, _ in rows}
        mounts = {pid: ['1 2 3 / ' + target + ' ro x' for target in ('/', '/proc', '/.sandbox/launcher')]
                  for pid, _, _ in rows}
        return rows, namespaces, mounts, dict(zip(('memory.swap.max', 'memory.max', 'pids.max', 'cpu.max'),
                                                 ('0', str(128 << 20), '64', '10000 10000')))

    def execute(self, samples, ticks):
        with tempfile.TemporaryDirectory() as temp:
            (Path(temp) / 'execution').mkdir()
            snapshot = Mock(side_effect=samples)
            output = Mock()
            context = dict(jobs=Path(temp), snapshot=snapshot, print=output,
                           time=types.SimpleNamespace(monotonic=Mock(side_effect=ticks), sleep=Mock()),
                           os=types.SimpleNamespace(readlink=lambda p: 'host-' + Path(p).name),
                           json=__import__('json'))
            exec(compile(self.sample, str(SOURCE), 'exec'), context)
            return snapshot, output

    def test_disappearance_discards_sample_then_requires_complete_facts(self):
        snapshot, output = self.execute([FileNotFoundError('retired process'), self.facts()], [0, .1, .2])
        self.assertEqual(snapshot.call_count, 2)
        self.assertEqual(output.call_count, 1)

    def test_no_complete_sample_within_original_deadline_fails(self):
        with self.assertRaisesRegex(AssertionError, 'did not observe'):
            self.execute([ProcessLookupError()], [0, .1, 2.1])

    def test_permission_failure_is_not_a_disappearing_process(self):
        with self.assertRaises(PermissionError):
            self.execute([PermissionError()], [0, .1])

    def test_unsafe_complete_sample_is_not_retried(self):
        for change in ('capability', 'namespace', 'mount', 'limit'):
            facts = copy.deepcopy(self.facts())
            if change == 'capability': facts[0][0][2]['CapEff'] = '1'
            if change == 'namespace': facts[1]['1']['mnt'] = 'host-mnt'
            if change == 'mount': facts[2]['1'][0] = '1 2 3 / / rw x'
            if change == 'limit': facts[3]['pids.max'] = '1024'
            with self.subTest(change=change), self.assertRaises(AssertionError):
                self.execute([facts], [0, .1])

    def test_namespace_read_failure_cannot_return_partial_thread_facts(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'cgroup.procs').write_text('1')
            thread = root / '1/task/1'
            thread.mkdir(parents=True)
            (thread / 'status').write_text('Uid: 61002 61002 61002 61002\n')
            context = dict(Path=Path, os=types.SimpleNamespace(readlink=Mock(side_effect=FileNotFoundError())))
            exec(compile(self.functions, str(SOURCE), 'exec'), context)
            with self.assertRaises(FileNotFoundError):
                context['snapshot'](root, root)
            # Namespace reads completed, but retirement before mountinfo is still incomplete.
            context['os'].readlink = Mock(return_value='namespace')
            with self.assertRaises(FileNotFoundError):
                context['snapshot'](root, root)
            self.assertEqual(context['os'].readlink.call_count, 7)


if __name__ == '__main__':
    unittest.main()
