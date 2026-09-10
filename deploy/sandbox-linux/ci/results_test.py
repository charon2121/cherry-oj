import json
from pathlib import Path
import tempfile
import unittest

import results
from report import manifest


class CompletionTests(unittest.TestCase):
    def test_boundary_rejects_any_skip_or_missing_completion(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'boundary'
            passed = ''.join('--- PASS: ' + name + ' (0.01s)\n' for name in (
                'TestStartupBoundaries', 'TestOutputPathBoundary', 'TestCgroupCapabilityRefusal', 'TestExecStageFailures'))
            text = passed + '--- PASS: TestExecFailureChild (0.00s)\n'
            path.write_text(text)
            results.boundary(path)
            for bad in [text.replace('--- PASS: TestOutputPathBoundary', '--- SKIP: TestOutputPathBoundary'),
                        text.replace('--- PASS: TestExecFailureChild', '--- SKIP: TestExecFailureChild'),
                        text.replace('--- PASS: TestStartupBoundaries', '--- PASS: OtherTest'),
                        text + '--- FAIL: TestStartupBoundaries/valid-go (0.1s)\n']:
                path.write_text(bad)
                with self.assertRaises(ValueError):
                    results.boundary(path)

    def test_repeat_requires_the_thousandth_iteration_and_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'repeat'
            rows = [{'snapshot': 'before'}, {'completed': 1000}, {'test': '1000'}, {'snapshot': 'after'}]
            text = '\n'.join(json.dumps(row) for row in rows) + '\nPASS repeat\n'
            path.write_text(text)
            results.chain(path, 'repeat')
            for bad in [text.replace('1000', '100'), text.replace('"after"', '"lost"'), text.replace('PASS repeat', '')]:
                path.write_text(bad)
                with self.assertRaises(ValueError):
                    results.chain(path, 'repeat')

    def test_empty_go_output_is_not_a_linux_run(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'go'
            path.write_text('Command completed with exit status 0.\n')
            with self.assertRaises(ValueError):
                results.linux_units(path)

    def test_go_requires_every_frozen_test_not_only_package_success(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'go'
            rows = []
            for package, names in manifest()['requiredGoTests'].items():
                rows += [dict(Action='pass', Package=package, Test=name) for name in names]
                rows.append(dict(Action='pass', Package=package))
            path.write_text('\n'.join(json.dumps(row) for row in rows))
            results.linux_units(path)
            path.write_text('\n'.join(json.dumps(row) for row in rows[1:]))
            with self.assertRaises(ValueError):
                results.linux_units(path)
