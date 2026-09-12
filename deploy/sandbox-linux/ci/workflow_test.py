"""Guard the reviewed workflow wiring; execute its Python orchestration with a fake command runner.

These checks do not replace GitHub's YAML/Actions validation or real Linux runs.
"""
import ast
import fnmatch
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import textwrap
import types
import unittest
from unittest.mock import patch

from report import ROOT

CI = ROOT / '.github/workflows/ci.yml'
COLD = ROOT / '.github/workflows/sandbox-download-cold.yml'


def jobs(text):
    section = text.split('\njobs:\n', 1)[1]
    matches = list(re.finditer(r'^  ([a-z][a-z0-9-]*):\n', section, re.M))
    return {match[1]: section[match.end():matches[i + 1].start() if i + 1 < len(matches) else len(section)]
            for i, match in enumerate(matches)}


def scripts(text):
    return [textwrap.dedent(match[1]).strip() for match in re.finditer(r'^        run: \|\n((?:          .*\n|\n)+)', text, re.M)]


def python_script(text):
    selected = [body for body in scripts(text) if body.startswith("python3 - <<'PYCODE'\n")]
    if len(selected) != 1:
        raise ValueError('expected one package orchestration script')
    body = selected[0].split('\n', 1)[1]
    if not body.endswith('\nPYCODE'):
        raise ValueError('unterminated Python heredoc')
    return body[:-len('\nPYCODE')]


class WorkflowTests(unittest.TestCase):
    def test_summary_covers_every_job_and_preserves_attempt_artifacts(self):
        from summary import JOBS, SUITES
        grouped = jobs(CI.read_text())
        self.assertEqual(set(grouped) - {'sandbox-summary'}, set(JOBS))
        summary = grouped['sandbox-summary']
        required = re.search(r'^    needs: \[([^\]]+)\]$', summary, re.M)
        self.assertEqual(set(required[1].split(', ')), set(JOBS))
        self.assertIn('    if: always()\n', summary)
        self.assertIn('      contents: read\n', summary)
        self.assertNotIn('continue-on-error', summary)
        self.assertNotIn('secrets.', summary)
        self.assertNotIn('sudo ', summary)
        for suite in SUITES:
            identity = 'sandbox-' + suite + '-${{ github.run_id }}-${{ github.run_attempt }}'
            self.assertIn('name: ' + identity, grouped['sandbox-' + suite])
            self.assertIn('name: ' + identity, summary)
        for name in ('sandbox-packages', 'sandbox-kernel', 'sandbox-native', 'sandbox-business'):
            artifacts = re.findall(r'^          name: (sandbox-.*)$', grouped[name], re.M)
            self.assertTrue(artifacts)
            self.assertTrue(all(v.endswith('${{ github.run_id }}-${{ github.run_attempt }}') for v in artifacts))

    def test_cold_full_baseline_bypasses_cache_without_deleting_it(self):
        text = CI.read_text()
        self.assertIn('      cold_packages:\n', text)
        job = jobs(text)['sandbox-packages']
        self.assertIn('if: inputs.cold_packages != true', job)
        self.assertIn("if: steps.cache.outputs.cache-hit != 'true' && inputs.cold_packages != true", job)
        self.assertNotIn('gh cache', job)

    def test_consumers_wait_and_verify_same_run_artifact(self):
        grouped = jobs(CI.read_text())
        for name, minutes in [('sandbox-kernel', 15), ('sandbox-native', 20), ('sandbox-business', 40)]:
            with self.subTest(name=name):
                job = grouped[name]
                self.assertIn('    needs: sandbox-packages\n', job)
                self.assertIn(f'    timeout-minutes: {minutes}\n', job)
                self.assertIn('name: sandbox-packages-${{ github.run_id }}-${{ github.run_attempt }}', job)
                self.assertIn('--packages "$RUNNER_TEMP/cherry-shared-packages"', job)
                self.assertNotIn('rootfs/download.py', job)
                self.assertNotIn('continue-on-error', job)
                self.assertIn('run: rm -rf -- "$RUNNER_TEMP/cherry-shared-packages"', job)
                self.assertNotRegex(job, r'^    if:', 'consumer must not be conditionally skipped')
                for forbidden in ('github-token:', 'run-id:', 'repository:', 'merge-multiple:'):
                    self.assertNotIn(forbidden, job)
                self.assertLess(job.index('actions/download-artifact@'), job.index('ci/prepare.py'))

    def test_cache_is_exact_and_published_only_after_verification(self):
        job = jobs(CI.read_text())['sandbox-packages']
        self.assertNotIn('restore-keys:', job)
        self.assertEqual(job.count('key: ${{ steps.identity.outputs.key }}'), 2)
        self.assertEqual(job.count('path: ${{ runner.temp }}/cherry-package-work/cache'), 2)
        self.assertIn("if: steps.cache.outputs.cache-hit != 'true'", job)
        self.assertLess(job.index("'--source', workspace / 'cache'"), job.index('actions/cache/save@'))
        self.assertLess(job.index("'--source', workspace / 'cache'"), job.index('actions/upload-artifact@'))
        self.assertNotIn('continue-on-error', job)
        self.assertIn('path: ${{ runner.temp }}/cherry-package-work/verified', job)

    def test_cold_routes_include_relevant_changes_and_exclude_unrelated_paths(self):
        text = COLD.read_text()
        expected = {'deploy/sandbox-linux/rootfs/**', 'deploy/sandbox-linux/ci/**',
                    'deploy/sandbox-linux/build-release.sh', '.github/workflows/ci.yml',
                    '.github/workflows/sandbox-download-cold.yml'}
        events = text.split('\nconcurrency:', 1)[0]
        for kind in ('push', 'pull_request'):
            match = re.search(r'^  ' + kind + r':\n(.*?)(?=^  [a-z_]+:|\Z)', events, re.M | re.S)
            self.assertIsNotNone(match)
            paths = set(re.findall(r"^      - '([^']+)'$", match[1], re.M))
            self.assertEqual(paths, expected)
            for path in ('deploy/sandbox-linux/rootfs/download.py', 'deploy/sandbox-linux/ci/packages.py', '.github/workflows/ci.yml'):
                self.assertTrue(any(fnmatch.fnmatchcase(path, pattern) for pattern in paths))
            for path in ('apps/web/src/example.tsx', 'development/README.md'):
                self.assertFalse(any(fnmatch.fnmatchcase(path, pattern) for pattern in paths))
        self.assertIn('branches: [main]', events)
        self.assertIn('  workflow_dispatch:', events)
        self.assertIn("cron: '0 2 * * 1'", events)
        self.assertNotIn('pull_request_target', text)
        self.assertNotIn('actions/cache', text)
        self.assertNotIn('actions/download-artifact', text)
        self.assertNotIn('--source', python_script(text))
        self.assertNotIn('continue-on-error', text)
        self.assertNotEqual(CI.read_text().splitlines()[0], text.splitlines()[0])
        self.assertIn('group: ${{ github.workflow }}-${{ github.ref }}', text)

    def run_preparation(self, text, hit):
        calls = []
        def run(argv, log, seconds, **_): calls.append((list(map(str, argv)), seconds))
        fake = types.SimpleNamespace(run=run, install_signal_handlers=lambda: None)
        with tempfile.TemporaryDirectory() as tmp, patch.dict(os.environ, RUNNER_TEMP=tmp, CACHE_HIT=hit), patch.dict(sys.modules, command=fake), patch.object(sys, 'path', list(sys.path)):
            body = python_script(text)
            exec(compile(body, '<workflow preparation>', 'exec'), {})
            self.assertTrue(list(Path(tmp).glob('*logs/identity.json')))
        return calls

    def test_hot_preparation_never_runs_download(self):
        calls = self.run_preparation(jobs(CI.read_text())['sandbox-packages'], 'true')
        self.assertEqual(len(calls), 1)
        self.assertIn('--source', calls[0][0])

    def test_miss_downloads_once_then_validates(self):
        calls = self.run_preparation(jobs(CI.read_text())['sandbox-packages'], '')
        self.assertEqual(len(calls), 2)
        self.assertNotIn('--source', calls[0][0])
        self.assertIn('--source', calls[1][0])
        self.assertEqual(calls[0][1], 600)

    def test_cold_always_downloads_and_builds_without_cache(self):
        calls = self.run_preparation(COLD.read_text(), 'true')
        self.assertEqual(len(calls), 2)
        self.assertIn('deploy/sandbox-linux/ci/packages.py', calls[0][0])
        self.assertNotIn('--source', calls[0][0])
        self.assertIn('deploy/sandbox-linux/rootfs/build.py', calls[1][0])
        self.assertEqual([seconds for _, seconds in calls], [600, 120])

    def test_new_actions_are_pinned_and_permissions_remain_read_only(self):
        selected = [jobs(CI.read_text())[name] for name in ('sandbox-packages', 'sandbox-kernel', 'sandbox-native', 'sandbox-business')]
        selected.append(jobs(COLD.read_text())['sandbox-download-cold'])
        for job in selected:
            self.assertIn('    permissions:\n      contents: read\n', job)
            self.assertNotRegex(job, r'^      \S+: write$', 'no repository write grants')
            self.assertIn('persist-credentials: false', job)
            for action in re.findall(r'uses: ([^\s]+)', job):
                self.assertRegex(action, r'^actions/[a-z-]+(?:/[a-z-]+)?@[0-9a-f]{40}$')

    def test_embedded_scripts_parse_without_executing_shell_commands(self):
        for text in (CI.read_text(), COLD.read_text()):
            for body in scripts(text):
                subprocess.run(['bash', '-n'], input=body, text=True, check=True, timeout=5,
                               stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
                if body.startswith("python3 - <<'PYCODE'\n"):
                    ast.parse(body.split('\n', 1)[1].removesuffix('\nPYCODE'))


if __name__ == '__main__':
    unittest.main()
