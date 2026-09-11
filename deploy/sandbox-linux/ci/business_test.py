"""Negative tests for the business harness; these do not replace real Linux/browser execution."""
import copy
import io
import json
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import Mock, patch
import zipfile

from business_api import API, MAX_BODY, failure_kind, fixture_zip
from business_config import DATABASES, create, environment
from business_evidence import Evidence, uuid
from business_observer import verify_observations
from business_resources import Dependencies
from business_results import LIVE_CASES, STATUSES, verify_live


def live_fixture():
    result = {key: dict(requestId='request-' + key, problemVersionId='version') for key in LIVE_CASES}
    for key, status in STATUSES.items():
        result[key].update(status=status, problemId='problem', httpNs=2_000_000_000, cpuNs=1_010_000_000,
            memoryBytes=1 << 20, bodyBytes=1024, effectiveLimits=dict(cpuNs=1000000000, memoryBytes=268435456))
    result['output'].update(stdoutTruncated=True, stdoutBytes=1 << 20)
    result['ac'].update(verdict='AC', passedCount=6, totalCount=6)
    result['wa'].update(verdict='WA')
    result['history'].update(draftPreserved=True, customPosts=8, formalPosts=2)
    return result


class BusinessTests(unittest.TestCase):
    def test_failure_diagnostics_only_export_exact_known_classification(self):
        self.assertEqual(failure_kind(json.dumps(dict(code='SERVICE_UNAVAILABLE',
            detail='身份服务配置不一致，请联系管理员。', secret='must-not-export'))), 'IDENTITY_CONFIGURATION_MISMATCH')
        for raw in ('private-password', '{}', '[]', '{"code":"PRIVATE_SECRET"}',
                    '{"code":"SERVICE_UNAVAILABLE","detail":"private-token"}'):
            self.assertEqual(failure_kind(raw), 'UNCLASSIFIED')

    def test_bootstrap_selects_one_shot_lifecycle_without_password_in_argv(self):
        import business_service
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp) / 'private'
            credentials = create(directory)
            argv = ['business_service.py', str(directory), 'user', '--java', '/java', '--bootstrap']
            with patch('sys.argv', argv), patch.object(business_service.os, 'dup2') as dup, patch.object(business_service.os, 'execve') as execute:
                business_service.main()
            args = execute.call_args.args
            self.assertIn('--cherry.auth.mode=bootstrap', args[1])
            self.assertEqual(args[2]['SPRING_PROFILES_ACTIVE'], 'test')
            self.assertNotIn(credentials['initialPassword'], str(args))
            self.assertEqual(dup.call_args.args[1], 0)

    def test_zip_contains_exactly_six_regular_pairs_with_correct_answers(self):
        with zipfile.ZipFile(io.BytesIO(fixture_zip())) as archive:
            self.assertEqual(set(archive.namelist()), {f'{i}.{suffix}' for i in range(1, 7) for suffix in ('in', 'out')})
            for entry in archive.infolist():
                self.assertTrue(stat.S_ISREG(entry.external_attr >> 16))
            for i in range(1, 7):
                self.assertEqual(sum(map(int, archive.read(f'{i}.in').split())), int(archive.read(f'{i}.out')))
        self.assertLess(len(fixture_zip()), 4096)

    def test_fresh_credentials_are_private_and_nonlocal_config_is_explicit(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            first, second = create(root / 'a'), create(root / 'b')
            for name in (*DATABASES, 'control', 'root', 'password'):
                self.assertNotEqual(first[name], second[name])
            for path in (root / 'a').glob('*'):
                self.assertEqual(path.stat().st_mode & 0o077, 0)
            env = environment(root / 'a', first)
            self.assertEqual(env['SPRING_PROFILES_ACTIVE'], 'test')
            self.assertEqual(env['CHERRY_SUBMISSION_PROBLEM_TOKEN'], env['CHERRY_SERVICE_CALLS_SUBMISSION_PROBLEM_TOKENS'])
            for name in DATABASES:
                self.assertIn('/cherry_ci_' + name, env['CHERRY_' + name.upper() + '_DB_URL'])
            self.assertTrue(all('application-local' not in value for value in env.values()))

    def test_mutating_http_failure_is_not_retried_or_logged_with_secret_body(self):
        api = API()
        api.csrf = dict(headerName='X-CSRF-Token', token='private-csrf')
        response = Mock(status=503, headers={'X-Request-Id': 'request-1'})
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        response.read.return_value = b'private-auth-grant'
        api.opener.open = Mock(return_value=response)
        with self.assertRaises(RuntimeError) as raised:
            api.request('POST', '/api/submissions', {'secret': 'private-secret'})
        self.assertEqual(api.opener.open.call_count, 1)
        self.assertNotIn('private', str(raised.exception))
        self.assertNotIn('private', json.dumps(api.events))

    def test_http_oversize_and_path_escape_fail_closed(self):
        api = API()
        api.csrf = dict(headerName='X-CSRF-Token', token='private-csrf')
        api.opener.open = Mock()
        for path, body in [('https://outside.invalid', b'x'), ('/api/submissions', b'x' * (MAX_BODY + 1))]:
            with self.assertRaises(ValueError):
                api.request('POST', path, body)
        api.opener.open.assert_not_called()
        response = Mock(status=200, headers={})
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        response.read.return_value = b'x' * (MAX_BODY + 1)
        api.opener.open.return_value = response
        with self.assertRaises(ValueError):
            api.request('GET', '/api/auth/csrf')

    def test_missing_case_duplicate_request_and_relaxed_budget_are_rejected(self):
        context = dict(problemId='problem', problemVersionId='version')
        verify_live(live_fixture(), context)
        def missing(v): del v['signal']
        def duplicate(v): v['re']['requestId'] = v['io']['requestId']
        def wrong_signal(v): v['signal']['status'] = 'TIME_LIMIT_EXCEEDED'
        def peak(v): v['empty']['memoryBytes'] = 256 << 20
        def budget(v): v['cpu']['effectiveLimits']['cpuNs'] = 2_000_000_000
        def retry(v): v['history']['formalPosts'] = 3
        def count(v): v['ac']['passedCount'] = 5
        for change in (missing, duplicate, wrong_signal, peak, budget, retry, count):
            value = live_fixture()
            change(value)
            with self.subTest(change=change.__name__), self.assertRaises(ValueError):
                verify_live(value, context)

    def test_cpu_wall_and_group_oom_are_independent_required_evidence(self):
        value = dict(error=None, maxSampleGapNs=2000000, records=[dict(case=key, firstSeenNs=1, goneNs=1_100_000_000,
                   cpu=dict(usage_usec=1010000), memoryEvents=dict(oom_kill=1)) for key in ('cpu', 'memory')])
        verify_observations(value, live_fixture())
        for change in ('missing', 'wall', 'oom', 'duplicate', 'cpu'):
            bad = copy.deepcopy(value)
            if change == 'missing': del bad['records'][0]['goneNs']
            if change == 'wall': bad['records'][0]['goneNs'] = 10_000_000_000
            if change == 'oom': bad['records'][1]['memoryEvents']['oom_kill'] = 0
            if change == 'duplicate': bad['records'].append(bad['records'][0])
            if change == 'cpu': bad['records'][0]['cpu']['usage_usec'] = 100
            with self.subTest(change=change), self.assertRaises(ValueError):
                verify_observations(bad, live_fixture())

    def test_cleanup_checks_all_ownership_before_removing_any_container(self):
        dependencies = Dependencies.__new__(Dependencies)
        dependencies.identity = '12-1'
        dependencies.data = dict(containers=['first', 'foreign'], volumes=[])
        dependencies.exists = Mock(return_value=True)
        dependencies.docker = Mock(side_effect=['12-1', 'someone-else'])
        with self.assertRaises(RuntimeError):
            dependencies.cleanup()
        self.assertTrue(all('rm' not in c.args for c in dependencies.docker.call_args_list))

    def test_existing_resource_is_never_claimed(self):
        dependencies = Dependencies.__new__(Dependencies)
        dependencies.exists = Mock(return_value=True)
        dependencies.save = Mock()
        dependencies.data = dict(containers=[])
        with self.assertRaises(RuntimeError):
            dependencies.claim('container', 'existing')
        dependencies.save.assert_not_called()

    def test_claim_is_durable_before_docker_create(self):
        dependencies = Dependencies.__new__(Dependencies)
        dependencies.exists = Mock(return_value=False)
        dependencies.data = dict(containers=[])
        dependencies.save = Mock()
        dependencies.claim('container', 'fresh')
        dependencies.save.assert_called_once()
        self.assertEqual(dependencies.data['containers'], ['fresh'])

    def test_one_cleanup_failure_does_not_leave_the_other_owned_backend_running(self):
        import business
        owned, dependencies, installation = Mock(), Mock(), Mock()
        owned.data = dict(units=['unit-1'])
        dependencies.cleanup.side_effect = RuntimeError('foreign label')
        with tempfile.TemporaryDirectory() as temp:
            with patch.object(business, 'BUSINESS_RECORD') as docker_record, patch.object(business, 'NATIVE_RECORD') as native_record, patch.object(business.Dependencies, 'load', return_value=dependencies), patch.object(business.Installation, 'load', return_value=installation):
                docker_record.exists.return_value = native_record.exists.return_value = True
                with self.assertRaises(RuntimeError):
                    business.cleanup(Path(temp), owned)
            installation.cleanup.assert_called_once()
            owned.cleanup.assert_not_called()  # Preserve outstanding ownership records.
            owned.stop.assert_called_once_with('unit-1')
            self.assertEqual(json.loads((Path(temp) / 'cleanup-failures.json').read_text()), ['RuntimeError'])

    def test_evidence_never_accepts_arbitrary_write_queries_or_ids(self):
        evidence = Evidence(Mock())
        for value in ('bad', "' OR 1=1", '../../data', 123):
            with self.assertRaises(ValueError):
                uuid(value)
        for database, query in [('judging', 'UPDATE judge_task SET status=1'), ('user', 'SELECT 1')]:
            with self.assertRaises(ValueError):
                evidence.rows(database, query)
        evidence.dependencies.docker.assert_not_called()

    def test_formal_chain_rejects_retried_or_unfinished_result(self):
        evidence = Evidence(Mock())
        for status, attempt in [('PENDING', 0), ('DONE', 2)]:
            evidence.rows = Mock(return_value=[dict(status=status, attempt=attempt)])
            with self.assertRaises(ValueError):
                evidence.formal({}, '00000000-0000-4000-8000-000000000001', 'source')


if __name__ == '__main__':
    unittest.main()
