"""Reject incomplete observations rather than silently converting them into successful samples."""
from pathlib import Path
import tempfile
import unittest

from diagnose_language import java_source, java_usage, wrappers


class LanguageDiagnosticTest(unittest.TestCase):
    def test_timeout_fact_survives_nil_wait_error(self):
        line = ('compile language=java usage={ExitCode:-1 Signal:9 CPUNs:3200000000 '
                'MemoryBytes:123456 ClockNs:5001000000 Reason:wall OOMKilled:false '
                'GroupAccounting:false} waitError=<nil>')
        result = java_usage(line)
        self.assertEqual(result['reason'], 'wall')
        self.assertEqual(result['signal'], 9)
        self.assertEqual(result['clockNs'], 5001000000)
        self.assertEqual(result['waitError'], '<nil>')

    def test_missing_or_duplicate_measurement_is_not_a_sample(self):
        line = ('compile language=java usage={ExitCode:0 Signal:0 CPUNs:1 MemoryBytes:2 '
                'ClockNs:3 Reason: OOMKilled:false GroupAccounting:false} waitError=<nil>')
        self.assertEqual(java_usage(line)['reason'], '')
        for text in ('PASS', line + '\n' + line):
            with self.subTest(text=text), self.assertRaises(ValueError):
                java_usage(text)

    def test_fixed_fixture_still_exercises_inner_class(self):
        source = java_source()
        self.assertIn('static class Pair', source)
        self.assertIn('new Pair(', source)

    def test_candidate_only_wraps_build_tools(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary) / 'bin'
            env = wrappers(directory, {'javac': '/path with spaces/javac', 'jar': '/path/jar'})
            self.assertEqual({p.name for p in directory.iterdir()}, {'javac', 'jar'})
            self.assertIn("exec '/path with spaces/javac' -J-XX:TieredStopAtLevel=1", (directory / 'javac').read_text())
            self.assertTrue(env['PATH'].startswith(str(directory)))


if __name__ == '__main__':
    unittest.main()
