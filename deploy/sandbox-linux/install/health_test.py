"""judge 是唯一的服务：就绪只看它的身份，其他目标一律报错。"""
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
    def check(self, status, body):
        connection = MagicMock()
        connection.getresponse.return_value = response(status, body)
        with patch.object(health.http.client, 'HTTPConnection', return_value=connection):
            return health.ready('judge')

    def test_judge_identity(self):
        self.assertTrue(self.check(200, {'name': 'cherry-oj-judge'}))
        self.assertFalse(self.check(200, {'name': 'cherry-oj-sandbox'}))
        self.assertFalse(self.check(503, {'name': 'cherry-oj-judge'}))

    def test_unknown_target_is_rejected(self):
        # sandbox 与 isolator 都已不是独立服务，传入它们必须报错，而不是永远等不到就绪。
        for target in ('sandbox', 'isolator'):
            with patch.object(health.sys, 'argv', ['health.py', target]):
                with self.assertRaises(ValueError):
                    health.main()

    def test_exited_service_fails_without_waiting_for_the_deadline(self):
        state = MagicMock(stdout='failed\n')
        with patch.object(health.sys, 'argv', ['health.py', 'judge']), \
                patch.object(health, 'ready', return_value=False), \
                patch.object(health.subprocess, 'run', return_value=state), \
                patch.object(health.time, 'sleep') as sleep:
            with self.assertRaisesRegex(RuntimeError, 'exited'):
                health.main()
        sleep.assert_not_called()

    def test_starting_service_keeps_waiting(self):
        state = MagicMock(stdout='active\n')
        answers = iter([False, False, True])
        with patch.object(health.sys, 'argv', ['health.py', 'judge']), \
                patch.object(health, 'ready', side_effect=lambda mode: next(answers)), \
                patch.object(health.subprocess, 'run', return_value=state), \
                patch.object(health.time, 'sleep'):
            health.main()

    def test_ready_rejects_other_targets(self):
        with self.assertRaises(ValueError):
            health.ready('sandbox')


if __name__ == '__main__':
    unittest.main()
