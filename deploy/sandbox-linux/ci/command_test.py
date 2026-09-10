from pathlib import Path
import sys
import tempfile
import time
import unittest

from command import LOG_LIMIT, run


class CommandTests(unittest.TestCase):
    def test_failure_timeout_and_large_output_are_bounded(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            cases = [('exit', 'raise SystemExit(7)', RuntimeError, 2),
                     ('timeout', 'import time; time.sleep(30)', TimeoutError, .1),
                     ('output', 'import os;\nwhile True: os.write(1,b"x"*65536)', RuntimeError, 2)]
            for name, source, error, timeout in cases:
                with self.subTest(name=name):
                    start = time.monotonic()
                    with self.assertRaises(error):
                        run([sys.executable, '-c', source], path / name, timeout)
                    self.assertLess(time.monotonic() - start, 5)
                    self.assertLessEqual((path / name).stat().st_size, LOG_LIMIT)

    def test_success_preserves_output_and_refuses_overwriting(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'result'
            run([sys.executable, '-c', 'print("actual output")'], path, 2)
            self.assertEqual(path.read_text(), 'actual output\n')
            with self.assertRaises(FileExistsError):
                run([sys.executable, '-c', 'print("replacement")'], path, 2)
