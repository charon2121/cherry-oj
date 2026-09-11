"""Check evidence bounds and failure propagation without any Linux services."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import memory_watch as memory
import native


class MemoryWatchTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_snapshot_separates_peak_from_sampled_composition(self):
        for key, value in {'memory.current': '100', 'memory.peak': '200',
                           'memory.swap.current': '0', 'pids.current': '2',
                           'memory.stat': 'anon 10\nfile 90\nunknown 123\n',
                           'memory.events': 'oom 1\noom_kill 1\n',
                           'memory.events.local': 'oom 1\noom_kill 1\n'}.items():
            (self.root / key).write_text(value)
        value = memory.snapshot(self.root)
        self.assertEqual(value['memory.peak'], 200)
        self.assertEqual(value['memory.current'], 100)
        self.assertEqual(value['stat'], {'anon': 10, 'file': 90})
        self.assertEqual(value['events']['oom_kill'], 1)
        (self.root / 'memory.stat').write_text('x' * 8193)
        with self.assertRaises(ValueError):
            memory.snapshot(self.root)

    def test_retains_highest_sample_but_bounds_tail_and_missing_evidence(self):
        samples = memory.Samples(self.root)
        with patch.object(memory, 'snapshot', return_value={'memory.current': 200}):
            samples.sample()
        with patch.object(memory, 'snapshot', return_value={'memory.current': 100}):
            for _ in range(1000):
                samples.sample()
        with patch.object(memory, 'snapshot', side_effect=FileNotFoundError):
            samples.sample()
        with patch.object(memory, 'snapshot', side_effect=ValueError('private text')):
            samples.sample()
        result = samples.report()
        self.assertEqual(result['samples'], 1001)
        self.assertEqual(len(result['tail']), 64)
        self.assertEqual(result['highestCurrent']['memory.current'], 200)
        self.assertEqual(result['missing'], 1)
        self.assertEqual(result['errors'], ['ValueError'])
        self.assertNotIn('private text', json.dumps(result))

    def test_install_failure_still_propagates_and_observer_stops(self):
        output = self.root / 'memory.json'
        with self.assertRaisesRegex(RuntimeError, 'installation failed'):
            with memory.observe(self.root / 'absent', output):
                raise RuntimeError('installation failed')
        report = json.loads(output.read_text())
        self.assertEqual(report['samples'], 0)
        self.assertIsNone(report['highestCurrent'])
        self.assertNotIn('error', report)

    def test_only_install_gets_larger_budget_and_memory_observation(self):
        from unittest.mock import Mock, MagicMock
        owned, report = Mock(), Mock()
        owned.identity = '123-1'
        report.output = self.root
        obj = native.Native(self.root, report, owned)
        with patch.object(native, 'observe', return_value=MagicMock()) as observe:
            obj.command('install', ['python3', 'manage.py'])
            for name in ('start', 'native', 'helper-config', 'rootfs-manifest', 'helper-binary',
                         'kill-judge', 'kill-sandbox', 'kill-helper', 'caps', 'uninstall'):
                obj.command(name, ['python3', 'verify.py'], 120 if name == 'caps' else 90)
        observe.assert_called_once_with(native.CGROUP / 'cherry-sandbox-test-work048-native-install-123-1.service',
                                        self.root / 'install-memory.json')
        self.assertEqual(owned.launch.call_args_list[0].kwargs, {'seconds': 90, 'memory': 256})
        for call in owned.launch.call_args_list[1:]:
            self.assertEqual(call.kwargs, {'seconds': 120 if call.args[2] == 'caps.log' else 90, 'memory': 128})

    def test_partial_progress_exports_only_counts_not_receipt_contents(self):
        from unittest.mock import Mock
        (self.root / 'installation.json').write_text(json.dumps(dict(status='installing',
            accounts={'private-name': {'groupCreated': True, 'userCreated': False}},
            files={'private-path': 'private-digest'}, token='private-token')))
        report = Mock(output=self.root)
        obj = native.Native(self.root, report, Mock(identity='123-1'))
        with patch.object(native, 'STATE', self.root), patch.object(native, 'run'):
            obj.diagnose()
        text = (self.root / 'install-progress.json').read_text()
        self.assertNotIn('private', text)
        self.assertEqual(json.loads(text), dict(receiptExists=True, releaseExists=False,
                         status='installing', groupsCreated=1, usersCreated=0, recordedFiles=1))

    def test_interrupted_receipt_write_does_not_hide_service_diagnostics(self):
        from unittest.mock import Mock
        obj = native.Native(self.root, Mock(output=self.root), Mock(identity='123-1'))
        for content in ('{"status":', 'null', '{"accounts": null}'):
            with self.subTest(content=content):
                (self.root / 'installation.json').write_text(content)
                with patch.object(native, 'STATE', self.root), patch.object(native, 'run') as run:
                    obj.diagnose()
                self.assertEqual(run.call_count, 2)
                self.assertIn('receiptReadError', json.loads((self.root / 'install-progress.json').read_text()))
