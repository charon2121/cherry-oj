#!/usr/bin/env python3
"""Require every job and current-attempt evidence; emit only fixed public facts."""
import argparse
import json
import os
from pathlib import Path
import re
import stat

from report import MAX_EVIDENCE_BYTES, case_ids, git_sha, read_json, validate, verify_files

JOBS = ('sandbox-packages', 'sandbox-basic', 'sandbox-kernel', 'sandbox-native', 'sandbox-business',
        'development', 'web', 'contracts', 'go', 'tidy', 'containers')
SUITES = ('basic', 'kernel', 'native', 'business')
RESULTS = {'success', 'failure', 'cancelled', 'skipped'}


def check_tree(directory):
    if not stat.S_ISDIR(directory.lstat().st_mode):
        raise ValueError('artifact root is not a directory')
    total = 0
    for index, path in enumerate(directory.rglob('*'), 1):
        info = path.lstat()
        if index > 4096:
            raise ValueError('too many artifact entries')
        if stat.S_ISDIR(info.st_mode):
            continue
        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
            raise ValueError('artifact contains a link or special file')
        total += info.st_size
        if total > MAX_EVIDENCE_BYTES:
            raise ValueError('artifact too large')


def summarize(directory, needs, source_sha, run_id, attempt):
    if (not re.fullmatch('[0-9a-f]{40}', source_sha) or not re.fullmatch('[1-9][0-9]*', run_id)
            or not re.fullmatch('[1-9][0-9]*', attempt)):
        raise ValueError('invalid current execution identity')
    jobs = {}
    exact_jobs = isinstance(needs, dict) and set(needs) == set(JOBS)
    for name in JOBS:
        value = needs.get(name) if isinstance(needs, dict) else None
        result = value.get('result') if isinstance(value, dict) else None
        jobs[name] = result if isinstance(result, str) and result in RESULTS else 'invalid'
    suites = {}
    for suite in SUITES:
        row = dict(status='FAIL', required=len(case_ids(suite)), passed=0)
        path = Path(directory) / f'sandbox-{suite}-{run_id}-{attempt}'
        try:
            check_tree(path)
            data = read_json(path / 'report.json')
            validate(data, suite, source_sha)
            if data['runId'] != run_id or data['runAttempt'] != attempt:
                raise ValueError('different execution attempt')
            verify_files(path, data)
            row.update(status='PASS', passed=len(data['cases']))
        except (OSError, ValueError, TypeError, KeyError, AttributeError):
            # Never export source-controlled report text, filenames or exception messages.
            pass
        suites[suite] = row
    success = exact_jobs and all(v == 'success' for v in jobs.values()) and all(
        row['status'] == 'PASS' for row in suites.values())
    return dict(schemaVersion=1, sourceSha=source_sha, runId=run_id, runAttempt=attempt,
                status='PASS' if success else 'FAIL', jobs=jobs, suites=suites)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reports', type=Path, required=True)
    parser.add_argument('--needs', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        needs = read_json(args.needs)
    except (OSError, ValueError):
        needs = None
    result = summarize(args.reports, needs, git_sha(),
                       os.environ['GITHUB_RUN_ID'], os.environ['GITHUB_RUN_ATTEMPT'])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
    return 0 if result['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
