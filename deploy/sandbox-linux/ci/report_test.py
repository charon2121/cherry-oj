import copy
import json
from pathlib import Path
import tempfile
import unittest
from report import Report, case_ids, read_json, validate, verify_files

SHA = 'a' * 40


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name) / 'report'
        self.report = Report('basic', self.output, SHA, 'test', {'system': 'fixture'})
        self.report.record(case_ids('basic'), 'PASS', ['unit.log'])
        (self.output / 'unit.log').write_text('test evidence')
        self.report.finish(True)

    def test_complete_evidence_passes(self):
        validate(read_json(self.output / 'report.json'), 'basic', SHA)
        verify_files(self.output, self.report.data)

    def test_missing_file_and_symlink_are_not_evidence(self):
        (self.output / 'unit.log').unlink()
        with self.assertRaises(ValueError):
            verify_files(self.output, self.report.data)
        (self.output / 'unit.log').symlink_to('/etc/hosts')
        with self.assertRaises(ValueError):
            verify_files(self.output, self.report.data)

    def test_harness_and_cleanup_evidence_must_agree(self):
        data = copy.deepcopy(self.report.data)
        data['harnessSha'] = '0' * 64
        with self.assertRaises(ValueError):
            verify_files(self.output, data)
        (self.output / 'cleanup.json').write_text('{"confirmed":false}')
        with self.assertRaises(ValueError):
            verify_files(self.output, self.report.data)

    def test_required_execution_cannot_be_hidden(self):
        for status in ['SKIP', 'NOT_RUN', 'FAIL', 'CANCELLED', 'ENVIRONMENT_ERROR']:
            with self.subTest(status=status):
                data = copy.deepcopy(self.report.data)
                data['cases'][0]['status'] = status
                with self.assertRaises(ValueError):
                    validate(data, 'basic', SHA)

    def test_missing_duplicate_unknown_and_wrong_revision_fail(self):
        def duplicate(d): d['cases'].append(copy.deepcopy(d['cases'][0]))
        def missing(d): d['cases'].pop()
        def version(d): d['schemaVersion'] = 2
        def revision(d): d['sourceSha'] = 'b' * 40
        def unfinished(d): d['finishedAt'] = None
        def unknown(d): d['unknown'] = True
        for change in [duplicate, missing, version, revision, unfinished, unknown]:
            with self.subTest(change=change.__name__):
                data = copy.deepcopy(self.report.data)
                change(data)
                with self.assertRaises(ValueError):
                    validate(data, 'basic', SHA)

    def test_cleanup_failure_and_path_escape_fail(self):
        for value in ['../secret', '/tmp/secret', 'logs/../../secret', './secret']:
            data = copy.deepcopy(self.report.data)
            data['cases'][0]['evidence'] = [value]
            with self.assertRaises(ValueError):
                validate(data, 'basic', SHA)
        data = copy.deepcopy(self.report.data)
        data['cleanup']['status'] = 'FAIL'
        with self.assertRaises(ValueError):
            validate(data, 'basic', SHA)

    def test_first_result_cannot_be_overwritten(self):
        with self.assertRaises(ValueError):
            self.report.record([case_ids('basic')[0]], 'PASS', ['retry.log'])

    def test_duplicate_json_keys_and_oversize_refused(self):
        path = self.output / 'bad.json'
        for content in ['{"status":"FAIL","status":"PASS"}', 'x' * ((1 << 20) + 1)]:
            path.write_text(content)
            with self.assertRaises(ValueError):
                read_json(path)


if __name__ == '__main__':
    unittest.main()
