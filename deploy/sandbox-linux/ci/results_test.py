import json
from pathlib import Path
import tempfile
import unittest

import results
from report import manifest


class CompletionTests(unittest.TestCase):
    def test_executor_suite_rejects_any_skip_failure_or_missing_completion(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'boundary'
            names = ('test_identity_and_filesystem', 'test_invalid_requests_are_refused', 'test_stale_group_is_reclaimed',
                     'test_missing_command_is_platform_failure', 'test_artifacts_are_bounded_regular_files',
                     'test_links_cannot_be_created')
            text = ''.join(f'{n} (__main__.ExecutorTest.{n}) ... ok\n' for n in names)
            text += '----------------------------------------------------------------------\nRan 6 tests in 1.0s\n\nOK\n'
            path.write_text(text)
            results.executor_suite(path)
            for bad in [text.replace('test_links_cannot_be_created (__main__.ExecutorTest.test_links_cannot_be_created) ... ok',
                                     'test_links_cannot_be_created (__main__.ExecutorTest.test_links_cannot_be_created) ... skipped'),
                        text.replace('test_identity_and_filesystem', 'test_other'),
                        text.replace('\nOK\n', '\nFAILED (failures=1)\n'),
                        text + 'test_extra (__main__.ExecutorTest.test_extra) ... ERROR\n']:
                path.write_text(bad)
                with self.assertRaises(ValueError):
                    results.executor_suite(path)

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
