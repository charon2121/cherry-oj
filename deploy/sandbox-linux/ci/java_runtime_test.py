#!/usr/bin/env python3
"""Java 回归不能把启动失败、诊断丢失或普通异常当作成功。"""
from __future__ import annotations

import unittest

from java_runtime import Case, validate


class JavaRuntimeTests(unittest.TestCase):
    def test_network_case_requires_code_to_reach_socket_without_class_loading_errors(self) -> None:
        case = Case('network', 'fixture', 'RE', 'ready\n')
        point = dict(idx=1, verdict='RE', output=dict(excerpt='ready\n'), stderr=dict(bytes=0, excerpt=''))
        validate(case, dict(verdict='RE', testcaseResults=[point]))
        for changed in (dict(output=dict(excerpt='')), dict(stderr=dict(bytes=20, excerpt='NoClassDefFoundError'))):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                validate(case, dict(verdict='RE', testcaseResults=[dict(point, **changed)]))

    def test_compile_failure_must_preserve_user_file_diagnostic_without_running(self) -> None:
        case = Case('core-compile-error', 'fixture', 'CE', message='Solution.java:4:')
        validate(case, dict(verdict='CE', testcaseResults=[], message='Solution.java:4: error'))
        validate(case, dict(verdict='CE', message='Solution.java:4: error'))
        for changed in (dict(message='Main.java:1: error'), dict(verdict='RE'),
                        dict(testcaseResults=None), dict(testcaseResults=[{}])):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                validate(case, dict(verdict='CE', testcaseResults=[], message='Solution.java:4: error') | changed)


if __name__ == '__main__':
    unittest.main()
