"""Negative controls for native evidence and ownership; never starts system services."""
import json
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import native_control as control
import native
import native_resources as resources
import native_results as results


class NativeEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.log = Path(self.temp.name) / 'native.log'

    def test_capability_results_require_all_seven_distinct_negative_controls(self):
        rows = [dict(test='seven-capability-set', result='PASS', nestedInput=True, privateOutput=True, descendantsReaped=True)]
        rows += [dict(test='remove-capability', removed=cap, startup='REFUSED') for cap in resources.CAPS]
        for i, row in enumerate(rows, 1):
            row.update(invocationID=format(i, '032x'), mainStartedNs=i*1000)
        def write(values):
            self.log.write_text('\n'.join(json.dumps(row) for row in values) + '\nOriginal deployment restored; no judge registration under temporary policy.\n')
        write(rows)
        results.check('caps', self.log, 'unused')
        for invalid in (rows[:-1], rows[:-1] + [rows[1]], rows[1:]):
            write(invalid)
            with self.assertRaises(ValueError):
                results.check('caps', self.log, 'unused')
        rows[-1]['invocationID'] = rows[-2]['invocationID']
        write(rows)
        with self.assertRaisesRegex(ValueError, 'distinct starts'):
            results.check('caps', self.log, 'unused')

    def test_prior_exit_code_cannot_prove_new_capability_start(self):
        path = resources.ROOT / 'deploy/sandbox-linux/install/verify-capabilities.py'
        spec = importlib.util.spec_from_file_location('capability_fixture', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        before = dict(invocationID='a'*32, mainStartedNs=1000)
        module.require_new_start(before, dict(invocationID='b'*32, mainStartedNs=2000))
        for after in (before, dict(invocationID='b'*32, mainStartedNs=1000), dict(invocationID='', mainStartedNs=2000)):
            with self.assertRaises(AssertionError):
                module.require_new_start(before, after)

    def test_failure_or_missing_recovery_marker_cannot_pass(self):
        self.log.write_text(json.dumps(dict(case='helper-binary', result='PASS')) + '\n')
        with self.assertRaises(ValueError):
            results.check('helper-binary', self.log, 'unused')
        self.log.write_text(json.dumps(dict(test='helper', result='PASS', recovered='RAN', fingerprint='old', killedPID=25, observedPayload=26, requestOutcome='SE')) + '\n')
        with self.assertRaises(ValueError):
            results.check('kill-helper', self.log, 'new')

    def test_native_requires_thread_and_resource_observations(self):
        row = dict(result='PASS', taskUIDs=[61002, 61003], capabilities=0, noNewPrivileges=1, namespaces=6,
                   readOnlyMounts=3, verifiedLimits=24, taskThreads=2, serviceThreads={'cherry-sandbox': 5, 'cherry-sandbox-judge': 5},
                   compileCpuNs=1, compileMemoryBytes=1, runCpuNs=1, runMemoryBytes=1)
        def check(value):
            self.log.write_text(json.dumps(value) + '\nNo task processes, execution cgroups or workspace files remain.\n')
            results.check('native', self.log, 'unused')
        check(row)
        for changed in ({'taskThreads': 0}, {'runMemoryBytes': 0}, {'verifiedLimits': 23}, {'capabilities': 1}):
            with self.assertRaises(ValueError):
                check(dict(row, **changed))

    def test_registration_requires_fresh_node_and_real_identity_fields(self):
        value = dict(nodeId='ci-test', environmentFingerprint='a'*64, sessionId='00000000-0000-4000-8000-000000000001',
                     endpoint='http://127.0.0.1:15051', architecture='amd64', cpuModel='test-cpu', osVersion='Linux',
                     kernelVersion='6.8', judgeVersion='test', sandboxVersion='test', configDigest='b'*64,
                     languages=[dict(languageId='cpp', toolchainVersion='locked-g++', languageConfigDigest='c'*64)])
        control.validate_registration(value, 'ci-test')
        for changed in ({'nodeId': 'old'}, {'architecture': 'arm64'}, {'configDigest': ''}, {'languages': []}):
            with self.assertRaises(ValueError):
                control.validate_registration(dict(value, **changed), 'ci-test')


class NativeOwnershipTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.unit_dir = self.root / 'units'
        self.unit_dir.mkdir()
        self.dropins = self.root / 'dropins'
        self.obj = resources.Installation.__new__(resources.Installation)
        self.obj.output = self.root
        self.obj.data = dict(run='1-1', units={u: 'a'*64 for u in resources.UNITS})
        for target, value in [('SYSTEMD', self.unit_dir), ('DROPINS', self.dropins)]:
            self.enterContext(patch.object(resources, target, value))
        self.enterContext(patch.object(resources, 'regular'))

    def test_unknown_unit_fragment_and_dropin_refuse_before_stop(self):
        for facts in ({'FragmentPath': '/unknown/unit'}, {'DropInPaths': '/unknown/dropin'}, {'UnitFileState': 'enabled'}):
            with patch.object(resources, 'properties', return_value=facts), patch.object(resources.subprocess, 'run') as run:
                with self.assertRaises(RuntimeError):
                    self.obj.cleanup()
                run.assert_not_called()

    def test_mutated_unit_refuses_cleanup(self):
        (self.unit_dir / resources.UNITS[0]).write_text('unrelated')
        with self.assertRaisesRegex(RuntimeError, 'unit changed'):
            self.obj.verify_units()

    def test_residual_tasks_prevent_account_and_file_removal(self):
        with patch.object(self.obj, 'verify_units'), patch.object(resources.subprocess, 'run') as run, \
             patch.object(resources, 'resources', return_value=dict(tasks=[25], mounts=[], cgroups=[])), \
             patch.object(resources.time, 'monotonic', side_effect=[0, 10]), patch.object(resources.shutil, 'rmtree') as remove:
            with self.assertRaisesRegex(RuntimeError, 'remain'):
                self.obj.cleanup()
            remove.assert_not_called()
            self.assertTrue(all(call.args[0][:2] == ['systemctl', 'stop'] for call in run.call_args_list))

    def test_only_known_capability_dropin_is_removable(self):
        self.dropins.mkdir()
        file = self.dropins / '90-work048-capability-test.conf'
        with patch.object(resources, 'properties', return_value={}):
            self.obj.verify_units()  # Cancellation between mkdir and writing the known drop-in.
            file.write_text(resources.capability_content(resources.CAPS))
            self.obj.verify_units()
            file.write_text('[Service]\nExecStart=/unknown\n')
            with self.assertRaisesRegex(RuntimeError, 'drop-in changed'):
                self.obj.verify_units()

    def test_load_rejects_another_runs_receipt(self):
        record = self.root / 'owner.json'
        record.write_text(json.dumps(dict(run='previous', units=self.obj.data['units'])))
        with patch.object(resources, 'RECORD', record), patch.object(resources.Owned, 'load'), \
             patch.object(resources, 'github_vm', return_value='1-1'):
            with self.assertRaisesRegex(RuntimeError, 'another run'):
                resources.Installation.load(self.root)

    def test_partial_install_uses_prior_claim_not_completed_production_receipt(self):
        record = self.root / 'owner.json'
        record.write_text(json.dumps(self.obj.data))
        with patch.object(resources, 'RECORD', record), patch.object(resources.Owned, 'load'), \
             patch.object(resources, 'github_vm', return_value='1-1'):
            loaded = resources.Installation.load(self.root)
            self.assertEqual(loaded.data, self.obj.data)
            self.assertFalse((self.root / 'installation.json').exists())

    def test_driver_stops_before_native_cleanup_and_cleanup_error_persists(self):
        events = []
        class Drivers:
            data = {'units': ['first', 'second']}
            def stop(self, *units):
                events.append(('stop', units))
            def cleanup(self):
                events.append(('remove',))
        class BrokenInstallation:
            def cleanup(self):
                events.append(('native',))
                raise RuntimeError('surviving native mount')
        record = self.root / 'owner.json'
        record.touch()
        with patch.object(native, 'RECORD', record), patch.object(native.Installation, 'load', return_value=BrokenInstallation()):
            with self.assertRaisesRegex(RuntimeError, 'surviving'):
                native.cleanup(self.root, Drivers())
        self.assertEqual(events, [('stop', ('second', 'first')), ('native',)])
        self.assertEqual(json.loads((self.root / 'cleanup-error.json').read_text())['message'], 'surviving native mount')


if __name__ == '__main__':
    unittest.main()
