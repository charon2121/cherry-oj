#!/usr/bin/env python3
"""运行时回归必须验证实际结果，不能只接受 HTTP 成功。"""
from __future__ import annotations

import json
import unittest
from unittest.mock import Mock, patch

from pypy_runtime import Case, run


class RuntimeTests(unittest.TestCase):
    def response(self, result: dict) -> Mock:
        response = Mock(status=200)
        response.read.return_value = json.dumps(result).encode()
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        return response

    def test_jit_must_run_and_produce_the_expected_output_without_startup_warnings(self) -> None:
        case = Case('jit', 'trusted fixture', 'RAN', 'jit-ok\n')
        point = dict(idx=1, verdict='RAN', output=dict(excerpt='jit-ok\n'), stderr=dict(bytes=0))
        valid = dict(verdict='RAN', testcaseResults=[point])
        with patch('pypy_runtime.urllib.request.urlopen', return_value=self.response(valid)) as opened:
            self.assertEqual(run('http://127.0.0.1:15051', (case,))[0]['name'], 'jit')
            body = json.loads(opened.call_args.args[0].data)
            self.assertEqual(body['languageId'], 'pypy')
            self.assertEqual(body['limits']['cpuNs'], case.cpu_ns)
        for changed in (dict(verdict='RE'), dict(testcaseResults=[]),
                        dict(testcaseResults=[dict(point, output=dict(excerpt='wrong'))]),
                        dict(testcaseResults=[dict(point, stderr=dict(bytes=61))])):
            with self.subTest(changed=changed), patch('pypy_runtime.urllib.request.urlopen',
                    return_value=self.response(dict(valid, **changed))), self.assertRaises(ValueError):
                run('http://127.0.0.1:15051', (case,))

    def test_a_module_error_cannot_masquerade_as_socket_permission_denial(self) -> None:
        case = Case('network', 'import socket\nsocket.socket()\n', 'RE')
        response = self.response(dict(verdict='RE', testcaseResults=[dict(idx=1, verdict='RE',
                                    stderr=dict(bytes=30, excerpt='ModuleNotFoundError'))]))
        with patch('pypy_runtime.urllib.request.urlopen', return_value=response), self.assertRaises(ValueError):
            run('http://127.0.0.1:15051', (case,))

    def test_runtime_fixtures_cannot_be_sent_to_external_or_credentialed_urls(self) -> None:
        with patch('pypy_runtime.urllib.request.urlopen') as opened:
            for endpoint in ('http://example.com', 'http://user@127.0.0.1', 'http://127.0.0.1/path'):
                with self.subTest(endpoint=endpoint), self.assertRaises(ValueError): run(endpoint)
            opened.assert_not_called()


if __name__ == '__main__':
    unittest.main()
