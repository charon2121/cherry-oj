"""Bounded, strict CI evidence. Missing execution never becomes a successful case."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import subprocess
import stat
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[3]
MANIFEST = Path(__file__).with_name('cases.json')
MAX_REPORT_BYTES = 1 << 20
STATUSES = {'PASS', 'FAIL', 'ENVIRONMENT_ERROR', 'CANCELLED', 'NOT_RUN'}
MAX_EVIDENCE_BYTES = 20 << 20


def harness_sha():
    """Include tracked and new source files, excluding private/ignored runtime data."""
    paths = subprocess.check_output(['git', '-c', 'safe.directory=' + str(ROOT), 'ls-files', '--cached', '--others', '--exclude-standard',
                                     '-z', 'deploy/sandbox-linux', 'apps/judge-engine/tests/sandbox-linux',
                                     'apps/web/e2e-live', 'apps/web/playwright.live.config.ts',
                                     'apps/web/tsconfig.node.json', 'apps/web/eslint.config.js', '.github/workflows/ci.yml', '.github/workflows/sandbox-download-cold.yml'], cwd=ROOT)
    value = hashlib.sha256()
    for name in sorted(set(paths.split(b'\0')) - {b''}):
        path = ROOT / name.decode()
        value.update(name + b'\0' + digest(path).encode() + b'\0')
    return value.hexdigest()


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda: source.read(65536), b''):
            value.update(chunk)
    return value.hexdigest()


def read_json(path):
    with Path(path).open('rb') as source:
        data = source.read(MAX_REPORT_BYTES + 1)
    if len(data) > MAX_REPORT_BYTES:
        raise ValueError('report exceeds byte limit')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('duplicate JSON key: ' + key)
            result[key] = value
        return result
    return json.loads(data, object_pairs_hook=unique)


def manifest():
    data = read_json(MANIFEST)
    if data['version'] != 1:
        raise ValueError('unknown manifest version')
    seen = set()
    for case in data['cases']:
        if case['id'] in seen or case['suite'] not in data['suites']:
            raise ValueError('duplicate case or unknown suite')
        seen.add(case['id'])
        for source in case['sources']:
            if not (ROOT / source['path']).is_file():
                raise ValueError('missing case source: ' + source['path'])
            if source.get('selector') and source['selector'] not in (ROOT / source['path']).read_text():
                raise ValueError('case source selector drift: ' + case['id'])
    return data


def case_ids(suite):
    data = manifest()
    if suite not in data['suites']:
        raise ValueError('unknown suite: ' + suite)
    return [case['id'] for case in data['cases'] if case['suite'] == suite]


def evidence_path(value):
    if not isinstance(value, str) or not re.fullmatch(r'[a-zA-Z0-9_./-]+', value):
        raise ValueError('invalid evidence path')
    path = Path(value)
    if path.is_absolute() or any(part in {'.', '..'} for part in value.split('/')):
        raise ValueError('evidence path escapes report directory')
    return path


def validate(data, suite, source_sha, successful=True):
    keys = {'schemaVersion', 'suite', 'sourceSha', 'harnessSha', 'runId', 'startedAt',
            'finishedAt', 'environment', 'cases', 'cleanup'}
    if not isinstance(data, dict) or set(data) != keys or data['schemaVersion'] != 1:
        raise ValueError('unknown report schema or fields')
    if data['suite'] != suite or data['sourceSha'] != source_sha or not re.fullmatch('[0-9a-f]{40}', source_sha):
        raise ValueError('report suite/source SHA mismatch')
    if not re.fullmatch('[0-9a-f]{64}', data['harnessSha']):
        raise ValueError('invalid harness digest')
    if not isinstance(data['environment'], dict) or not data['runId']:
        raise ValueError('missing execution context')
    if not data['startedAt'] or not data['finishedAt']:
        raise ValueError('unfinished report')
    observed = set()
    for case in data['cases']:
        if set(case) != {'id', 'status', 'evidence', 'details'} or case['id'] in observed:
            raise ValueError('duplicate case or invalid fields')
        observed.add(case['id'])
        if case['status'] not in STATUSES or not isinstance(case['details'], dict):
            raise ValueError('invalid case status/details')
        if not isinstance(case['evidence'], list):
            raise ValueError('invalid evidence list')
        for item in case['evidence']:
            evidence_path(item)
        if case['status'] == 'PASS' and not case['evidence']:
            raise ValueError('passed case without evidence')
        if successful and case['status'] != 'PASS':
            raise ValueError('required case did not pass: ' + case['id'])
    if observed != set(case_ids(suite)):
        raise ValueError('missing or unexpected required cases')
    cleanup = data['cleanup']
    if not isinstance(cleanup, dict) or set(cleanup) != {'status', 'evidence'}:
        raise ValueError('invalid cleanup report')
    evidence_path(cleanup['evidence'])
    if cleanup['status'] not in STATUSES or (successful and cleanup['status'] != 'PASS'):
        raise ValueError('cleanup was not confirmed')
    return data


def verify_files(directory, data):
    """Check downloaded evidence before accepting it; never follow artifact links."""
    directory = Path(directory)
    total = 0
    for path in directory.rglob('*'):
        mode = path.lstat().st_mode
        if stat.S_ISDIR(mode):
            continue
        if not stat.S_ISREG(mode) or path.stat().st_nlink != 1:
            raise ValueError('non-regular or hardlinked evidence')
        total += path.stat().st_size
    if total > MAX_EVIDENCE_BYTES:
        raise ValueError('suite evidence exceeds byte limit')
    required = {data['cleanup']['evidence']}
    required.update(item for case in data['cases'] for item in case['evidence'])
    for name in required:
        path = directory / evidence_path(name)
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError('missing or empty evidence: ' + name)
    if read_json(directory / data['cleanup']['evidence']) != {'confirmed': True}:
        raise ValueError('cleanup evidence disagrees with report')
    if data['harnessSha'] != harness_sha():
        raise ValueError('evidence was produced by a different harness')


class Report:
    def __init__(self, suite, output, source_sha, run_id, environment):
        self.output = Path(output)
        self.output.mkdir(parents=True, exist_ok=False)
        self.data = dict(schemaVersion=1, suite=suite, sourceSha=source_sha,
                         harnessSha=harness_sha(), runId=run_id, startedAt=timestamp(),
                         finishedAt=None, environment=environment,
                         cases=[dict(id=key, status='NOT_RUN', evidence=[], details={}) for key in case_ids(suite)],
                         cleanup=dict(status='NOT_RUN', evidence='cleanup.json'))
        self.save()

    def record(self, ids, status, evidence, **details):
        for key in ids:
            matches = [item for item in self.data['cases'] if item['id'] == key]
            if len(matches) != 1 or matches[0]['status'] != 'NOT_RUN':
                raise ValueError('unknown or already recorded case: ' + key)
            matches[0].update(status=status, evidence=evidence, details=details)
        self.save()

    def finish(self, cleanup):
        self.data['finishedAt'] = timestamp()
        self.data['cleanup']['status'] = 'PASS' if cleanup else 'FAIL'
        (self.output / 'cleanup.json').write_text(json.dumps({'confirmed': bool(cleanup)}) + '\n')
        self.save()

    def save(self):
        encoded = json.dumps(self.data, ensure_ascii=False, indent=2).encode()
        if len(encoded) > MAX_REPORT_BYTES:
            raise ValueError('report exceeds byte limit')
        temp = self.output / 'report.pending'
        temp.write_bytes(encoded + b'\n')
        temp.replace(self.output / 'report.json')


def git_sha():
    return subprocess.check_output(['git', '-c', 'safe.directory=' + str(ROOT), 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
