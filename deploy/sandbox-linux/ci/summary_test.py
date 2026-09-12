"""Exercise the real aggregation boundary with complete and damaged evidence."""
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from report import Report, case_ids, git_sha
from summary import JOBS, SUITES, summarize

SHA = 'a' * 40


class SummaryTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.needs = {name: dict(result='success', outputs={}) for name in JOBS}
        with patch.dict(os.environ, GITHUB_RUN_ATTEMPT='2'):
            for suite in SUITES:
                path = self.root / f'sandbox-{suite}-123-2'
                report = Report(suite, path, SHA, '123', {})
                (path / 'facts.json').write_text('{"public":true}\n')
                report.record(case_ids(suite), 'PASS', ['facts.json'])
                report.finish(True)
        self.path = self.root / 'sandbox-kernel-123-2'

    def result(self):
        return summarize(self.root, self.needs, SHA, '123', '2')

    def test_complete_same_attempt_is_the_only_green(self):
        result = self.result()
        self.assertEqual(result['status'], 'PASS')
        self.assertEqual(sum(r['passed'] for r in result['suites'].values()), 93)
        self.assertEqual(result['runAttempt'], '2')

    def test_every_predecessor_must_succeed(self):
        for job in JOBS:
            for status in ('failure', 'cancelled', 'skipped', 'private-secret', None):
                with self.subTest(job=job, status=status):
                    self.needs[job]['result'] = status
                    result = self.result()
                    self.assertEqual(result['status'], 'FAIL')
                    self.assertNotIn('private-secret', json.dumps(result))
            self.needs[job]['result'] = 'success'
        self.needs['unexpected'] = dict(result='success')
        self.assertEqual(self.result()['status'], 'FAIL')
        self.needs = {}
        self.assertEqual(self.result()['status'], 'FAIL')

    def test_report_identity_case_and_cleanup_corruption_fail(self):
        path = self.path / 'report.json'
        original = json.loads(path.read_text())
        variants = []
        for field, value in [('sourceSha', 'b' * 40), ('runId', '124'), ('runAttempt', '1'),
                             ('harnessSha', '0' * 64), ('schemaVersion', 1)]:
            variants.append(dict(original, **{field: value}))
        variants.append(dict(original, cases=original['cases'][:-1]))
        variants.append(dict(original, cases=original['cases'] + [original['cases'][0]]))
        for status in ('FAIL', 'ENVIRONMENT_ERROR', 'CANCELLED', 'NOT_RUN'):
            value = copy.deepcopy(original)
            value['cases'][0]['status'] = status
            variants.append(value)
        variants.append(dict(original, cleanup=dict(status='FAIL', evidence='cleanup.json')))
        for value in variants:
            with self.subTest(value=value):
                path.write_text(json.dumps(value))
                self.assertEqual(self.result()['status'], 'FAIL')
        path.unlink()
        self.assertEqual(self.result()['status'], 'FAIL')

    def test_missing_evidence_and_links_fail_before_reading(self):
        path = self.path / 'facts.json'
        path.unlink()
        self.assertEqual(self.result()['status'], 'FAIL')
        path.symlink_to(self.path / 'cleanup.json')
        self.assertEqual(self.result()['status'], 'FAIL')
        path.unlink()
        os.link(self.path / 'cleanup.json', path)
        self.assertEqual(self.result()['status'], 'FAIL')
        path.unlink()
        moved = self.root / 'moved'
        self.path.rename(moved)
        self.path.symlink_to(moved, target_is_directory=True)
        self.assertEqual(self.result()['status'], 'FAIL')

    def test_prior_attempt_artifact_cannot_fill_missing_current_artifact(self):
        self.path.rename(self.root / 'sandbox-kernel-123-1')
        self.assertEqual(self.result()['status'], 'FAIL')

    def test_cli_exit_status_and_fixed_output_follow_the_real_verifier(self):
        for path in self.root.glob('sandbox-*/report.json'):
            value = json.loads(path.read_text())
            value['sourceSha'] = git_sha()
            path.write_text(json.dumps(value))
        needs = self.root / 'needs.json'
        output = self.root / 'summary.json'
        command = [sys.executable, str(Path(__file__).with_name('summary.py')), '--reports', str(self.root),
                   '--needs', str(needs), '--output', str(output)]
        environment = dict(os.environ, GITHUB_RUN_ID='123', GITHUB_RUN_ATTEMPT='2')
        for status, code in [('success', 0), ('cancelled', 1), ('failure', 1)]:
            self.needs['go']['result'] = status
            needs.write_text(json.dumps(self.needs))
            result = subprocess.run(command, env=environment, capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, code, result.stderr)
            self.assertEqual(json.loads(output.read_text())['status'], 'PASS' if code == 0 else 'FAIL')
        needs.write_text('{"private-secret":')
        result = subprocess.run(command, env=environment, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 1)
        self.assertNotIn('private-secret', result.stdout + result.stderr + output.read_text())


if __name__ == '__main__':
    unittest.main()
