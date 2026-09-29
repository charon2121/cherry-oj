"""sandbox 必须报告 linux 隔离才算就绪：零隔离的开发后端不能放行依赖它的 judge。"""
import json
import unittest
from unittest.mock import MagicMock, patch

import health


def response(status, body):
    r = MagicMock()
    r.status = status
    r.read.return_value = json.dumps(body).encode()
    return r


class ReadinessTests(unittest.TestCase):
    def check(self, status, body, mode='sandbox'):
        connection = MagicMock()
        connection.getresponse.return_value = response(status, body)
        with patch.object(health.http.client, 'HTTPConnection', return_value=connection):
            return health.ready(mode)

    def test_sandbox_requires_linux_isolation(self):
        self.assertTrue(self.check(200, {'name': 'cherry-oj-sandbox', 'isolation': 'linux'}))
        self.assertFalse(self.check(200, {'name': 'cherry-oj-sandbox', 'isolation': 'devhost'}))
        self.assertFalse(self.check(200, {'name': 'other', 'isolation': 'linux'}))
        self.assertFalse(self.check(503, {'name': 'cherry-oj-sandbox', 'isolation': 'linux'}))

    def test_unknown_target_is_rejected(self):
        # isolator 已不是独立服务，传入它必须报错，而不是永远等不到就绪。
        with patch.object(health.sys, 'argv', ['health.py', 'isolator']):
            with self.assertRaises(ValueError):
                health.main()

    def test_judge_identity(self):
        self.assertTrue(self.check(200, {'name': 'cherry-oj-judge'}, 'judge'))
        self.assertFalse(self.check(200, {'name': 'cherry-oj-sandbox'}, 'judge'))


if __name__ == '__main__':
    unittest.main()
