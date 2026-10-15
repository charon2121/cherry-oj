import unittest

import specs
from corpus_base import CorpusError


def parse(value, testcases=("1", "2")):
    return specs.parse_run_specs(value, "x", testcases)


class ParseTest(unittest.TestCase):
    def test_minimal_and_full(self):
        (spec,) = parse([{"facts": {"reason": "cpu"}}])
        self.assertEqual(spec.assertion.facts, {"reason": "cpu"})
        (spec,) = parse([{"testcase": "1", "limits": {"cpuNs": 5, "maxProcesses": 8}, "facts": {"exitCode": 0},
                          "stdout": {"equals": "x"}, "stderr": {"contains": "y"}, "elapsedMsLt": 100}])
        self.assertEqual((spec.testcase, spec.limits, spec.elapsed_ms_lt), ("1", {"cpuNs": 5, "maxProcesses": 8}, 100))

    def test_rejects_bad_specs(self):
        bad = [
            ([], "非空数组"),
            ([{}], "至少要有"),
            ([{"facts": {"colour": 1}}], "不认识的事实字段"),
            ([{"facts": {"exitCode": {"gt": "x"}}}], "必须是数字"),
            ([{"facts": {"exitCode": {"between": 1}}}], "组成"),
            ([{"facts": {"exitCode": [1]}}], "不能是数组"),
            ([{"facts": {"reason": "x"}, "testcase": "9"}], "不是所属题目的测试点"),
            ([{"facts": {"reason": "x"}, "testcase": "1", "stdin": "a"}], "互斥"),
            ([{"facts": {"reason": "x"}, "limits": {"cpuNs": 0}}], "正整数"),
            ([{"facts": {"reason": "x"}, "limits": {"weight": 1}}], "未知字段"),
            ([{"id": "A", "facts": {"reason": "x"}}], "id 必须匹配"),
            ([{"id": "a", "facts": {"reason": "x"}}, {"id": "a", "facts": {"reason": "y"}}], "重复"),
            ([{"facts": {"reason": "x"}, "concurrent": ["b"]}], "必须有 id"),
            ([{"id": "a", "facts": {"reason": "x"}, "concurrent": ["b"]}], "不存在"),
            ([{"id": "a", "facts": {"reason": "x"}, "concurrent": ["b"]},
              {"id": "b", "facts": {"reason": "x"}}], "对称"),
            ([{"anyOf": [{"facts": {"reason": "x"}}], "facts": {"reason": "x"}}], "互斥"),
            ([{"anyOf": [{"facts": {"reason": "x"}}]}], "至少两项"),
        ]
        for value, text in bad:
            with self.assertRaises(CorpusError, msg=str(value)) as caught:
                parse(value)
            self.assertIn(text, str(caught.exception), value)

    def test_attack_samples_cannot_use_testcase(self):
        with self.assertRaises(CorpusError):
            specs.parse_run_specs([{"testcase": "1", "facts": {"reason": "x"}}], "x", None)

    def test_groups(self):
        parsed = parse([{"facts": {"reason": "a"}},
                        {"id": "l", "concurrent": ["r"], "facts": {"reason": "b"}},
                        {"id": "r", "concurrent": ["l"], "facts": {"reason": "c"}},
                        {"facts": {"reason": "d"}}])
        self.assertEqual([[s.assertion.facts["reason"] for s in g] for g in specs.groups(parsed)],
                         [["a"], ["b", "c"], ["d"]])


def result(**facts):
    return specs.RunResult({"exitCode": 0, "reason": "", "cpuNs": 1_100_000_000, "cancelled": False, **facts},
                           "out", "err", 120.0)


class EvaluateTest(unittest.TestCase):
    def check(self, spec, observed):
        (parsed,) = parse([spec])
        return specs.evaluate(parsed, observed)

    def test_literals_are_type_strict(self):
        self.assertEqual(self.check({"facts": {"exitCode": 0}}, result()), [])
        self.assertTrue(self.check({"facts": {"exitCode": 1}}, result()))
        self.assertTrue(self.check({"facts": {"cancelled": 0}}, result()))  # 0 不等于 false
        self.assertEqual(self.check({"facts": {"cancelled": False}}, result()), [])

    def test_comparisons_and_ranges(self):
        spec = {"facts": {"cpuNs": {"ge": 1_000_000_000, "lt": 1_200_000_000}}}
        self.assertEqual(self.check(spec, result()), [])
        self.assertTrue(self.check(spec, result(cpuNs=1_300_000_000)))
        self.assertTrue(self.check(spec, result(cpuNs=900_000_000)))
        self.assertEqual(self.check({"facts": {"reason": {"in": ["cpu", "wall"]}}}, result(reason="wall")), [])
        self.assertTrue(self.check({"facts": {"reason": {"in": ["cpu", "wall"]}}}, result(reason="")))
        self.assertTrue(self.check({"facts": {"cpuNs": {"gt": 5}}}, result(cpuNs=True)))  # 布尔不是数字

    def test_missing_fact_is_reported(self):
        self.assertIn("没有给出", self.check({"facts": {"signal": 9}}, result())[0])

    def test_streams_and_elapsed(self):
        self.assertEqual(self.check({"stdout": {"equals": "out"}, "stderr": {"startsWith": "e"}}, result()), [])
        self.assertTrue(self.check({"stdout": {"equals": "no"}}, result()))
        self.assertTrue(self.check({"facts": {"exitCode": 0}, "elapsedMsLt": 100}, result()))
        self.assertEqual(self.check({"facts": {"exitCode": 0}, "elapsedMsLt": 500}, result()), [])

    def test_any_of(self):
        spec = {"anyOf": [{"facts": {"signal": 31}}, {"facts": {"exitCode": 0}, "stdout": {"equals": "out"}}]}
        self.assertEqual(self.check(spec, result(signal=0)), [])
        self.assertEqual(self.check(spec, result(signal=31, exitCode=-1)), [])
        diffs = self.check(spec, result(signal=9, exitCode=-1))
        self.assertIn("anyOf", diffs[0])

    def test_summaries_show_only_asserted_facts(self):
        (spec,) = parse([{"facts": {"reason": "cpu"}}])
        self.assertEqual(specs.expected_summary(spec), {"reason": "cpu"})
        self.assertEqual(specs.actual_summary(spec, result(reason="cpu")), {"reason": "cpu"})


if __name__ == "__main__":
    unittest.main()
