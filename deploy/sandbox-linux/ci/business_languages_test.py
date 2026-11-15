#!/usr/bin/env python3
"""验证多语言回归的账号绑定、历史证据与失败边界。"""
from __future__ import annotations

import json
import unittest
from unittest.mock import Mock

from business_api import API
from business_evidence import Evidence
from business_languages import run, submit, trial


class LanguageTests(unittest.TestCase):
    def test_api_preserves_owner_and_idempotency_without_logging_private_bodies(self) -> None:
        api = API(base_url='http://127.0.0.1:18080', origin='http://127.0.0.1:18080')
        api.csrf = dict(headerName='X-CSRF-Token', token='private-csrf')
        response = Mock(status=201, headers={'X-Request-Id': 'request-1'})
        response.read.return_value = b'{"data":{"id":"submission-1"}}'
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        api.opener.open = Mock(return_value=response)
        api.request('POST', '/api/submissions', dict(source='private-source'), expected=201,
                    headers={'X-Expected-User-Id': 'owner', 'Idempotency-Key': 'key-1'})
        request = api.opener.open.call_args.args[0]
        headers = {key.lower(): value for key, value in request.header_items()}
        self.assertEqual(request.full_url, 'http://127.0.0.1:18080/api/submissions')
        self.assertEqual(headers['origin'], 'http://127.0.0.1:18080')
        self.assertEqual(headers['x-expected-user-id'], 'owner')
        self.assertEqual(headers['idempotency-key'], 'key-1')
        self.assertEqual(headers['x-csrf-token'], 'private-csrf')
        self.assertNotIn('private-', json.dumps(api.events))
        api.opener.open.assert_called_once()  # 写请求没有隐式重试。

    def test_frozen_query_is_bounded_and_rejects_external_sql_fragments(self) -> None:
        evidence = Evidence(Mock())
        evidence.rows = Mock(return_value=[dict(sha256='a' * 64)])
        evidence.frozen('00000000-0000-4000-8000-000000000001')
        database, query = evidence.rows.call_args.args
        self.assertEqual(database, 'submission')
        self.assertTrue(query.startswith('SELECT '))
        self.assertIn('LIMIT 2', query)
        self.assertNotIn('completeSource', query)
        for value in ("' OR 1=1", 'not-a-uuid'):
            with self.assertRaises(ValueError):
                evidence.frozen(value)
        for rows in ([], [{}, {}]):
            evidence.rows.return_value = rows
            with self.assertRaises(ValueError):
                evidence.frozen('00000000-0000-4000-8000-000000000001')

    def test_wrong_language_or_verdict_never_becomes_a_successful_submission(self) -> None:
        for language, verdict in (('c', 'WA'), ('cpp', 'AC')):
            api = Mock(user_id='owner')
            api.request.side_effect = [dict(id='s'), dict(id='s', status='DONE', languageId=language, verdict=verdict)]
            with self.subTest(language=language, verdict=verdict), self.assertRaises(ValueError):
                submit(api, 'problem', 'c', 'private-source')

    def test_trial_must_preserve_limits_and_execute_expected_input(self) -> None:
        valid = dict(status='COMPLETED', stdout=dict(text='8\n'),
                     effectiveLimits=dict(cpuNs=1_000_000_000, memoryBytes=128 << 20))
        api = Mock()
        api.request.return_value = valid
        trial(api, 'p', 'c', 'code', 1_000_000_000, 128 << 20)
        for changed in (dict(status='TIME_LIMIT_EXCEEDED'), dict(stdout=dict(text='3\n')),
                        dict(effectiveLimits=dict(cpuNs=2_000_000_000, memoryBytes=128 << 20))):
            api.request.return_value = dict(valid, **changed)
            with self.assertRaises(ValueError):
                trial(api, 'p', 'c', 'code', 1_000_000_000, 128 << 20)

    def test_missing_owner_or_invalid_run_identity_cannot_create_problems(self) -> None:
        for owner, identity in ((None, 'test-1'), ('owner', '../other')):
            api = Mock(user_id=owner)
            with self.assertRaises(ValueError):
                run(api, identity, Mock())
            api.request.assert_not_called()


if __name__ == '__main__':
    unittest.main()
