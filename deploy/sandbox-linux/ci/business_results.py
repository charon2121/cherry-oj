"""Require every live case, bounded facts, and current build identity before reporting PASS."""

from pathlib import Path
import stat
import xml.etree.ElementTree as ET

from business_config import SERVICES
from report import ROOT, digest, git_sha, harness_sha, read_json

LIVE_CASES = ('io', 'ce', 're', 'signal', 'cpu', 'memory', 'output', 'empty', 'ac', 'wa', 'history')
STATUSES = dict(zip(LIVE_CASES[:8], ('COMPLETED', 'COMPILE_ERROR', 'RUNTIME_ERROR', 'RUNTIME_ERROR',
                                   'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'OUTPUT_LIMIT_EXCEEDED', 'COMPLETED')))


def browser_diagnostic(path):
    """Validate private reporter output before copying it to public artifacts."""
    info = Path(path).lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_size > 16_384:
        raise ValueError('untrusted or oversized browser diagnostic')
    value = read_json(path)
    if not isinstance(value, dict) or set(value) != {'phase', 'status', 'failures'}:
        raise ValueError('invalid browser diagnostic')
    if value['phase'] not in ('startup', 'login', 'problem', *LIVE_CASES) or value['status'] not in (
            'passed', 'failed', 'timedout', 'interrupted'):
        raise ValueError('unknown browser diagnostic classification')
    if not isinstance(value['failures'], list) or len(value['failures']) > 16:
        raise ValueError('too many browser diagnostic locations')
    for row in value['failures']:
        if not isinstance(row, dict) or set(row) != {'file', 'line', 'column'} or row['file'] not in (
                'business.spec.ts', 'support.ts', 'schemas.ts', 'reporter.ts', 'playwright.live.config.ts'):
            raise ValueError('unknown browser source location')
        if any(type(row[key]) is not int or not 1 <= row[key] <= 100_000 for key in ('line', 'column')):
            raise ValueError('invalid browser source position')
    return value

AUTHENTICATION_TESTS = {
    'com.cherryoj.userservice.application.AuthenticationServiceTests': {
        'authenticationIssuesDatabaseRepresentableDeadlineWithoutExtendingLifetime',
        'failedPasswordCommitsBackoffBeforeReturningGenericFailure',
        'validateIsReadOnlyAndReturnsUnchangedAbsoluteDeadline',
        'exchangeRecordsUsageButNeverExtendsAbsoluteDeadline'},
    'com.cherryoj.userservice.persistence.UserPersistenceIntegrationTests': {
        'flywayAndMappersPreserveAccountInvariants',
        'failedLoginBackoffSurvivesTheGenericAuthenticationException',
        'mysqlValidationIgnoresLegacyIdleAndNeverExtendsAbsoluteDeadline',
        'authenticatedDeadlineSurvivesMysqlRoundTripAtNanosecondPrecision'}}


def authentication_results(directory):
    """Read only the two expected fresh Surefire files; discard properties, output and failure bodies."""
    results = {}
    for suite, methods in AUTHENTICATION_TESTS.items():
        path = Path(directory) / ('TEST-' + suite + '.xml')
        info = path.lstat()
        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_size > 2 << 20:
            raise ValueError('untrusted or oversized authentication report')
        raw = path.read_bytes()
        if b'<!DOCTYPE' in raw or b'<!ENTITY' in raw:
            raise ValueError('authentication report must not contain XML declarations')
        root = ET.fromstring(raw)
        if root.tag != 'testsuite' or root.get('name') != suite:
            raise ValueError('authentication test suite mismatch')
        cases = root.findall('testcase')
        if len(cases) != len(methods) or int(root.get('tests', '-1')) != len(cases):
            raise ValueError('authentication test count mismatch')
        found = {}
        for case in cases:
            name = case.get('name')
            if case.get('classname') != suite or name not in methods or name in found:
                raise ValueError('missing, duplicate or unexpected authentication test')
            outcome = [key for key in ('failure', 'error', 'skipped') if case.find(key) is not None]
            if len(outcome) > 1:
                raise ValueError('ambiguous authentication test outcome')
            found[name] = outcome[0].upper() if outcome else 'PASS'
        for attribute, status in [('failures', 'FAILURE'), ('errors', 'ERROR'), ('skipped', 'SKIPPED')]:
            if int(root.get(attribute, '-1')) != list(found.values()).count(status):
                raise ValueError('authentication summary disagrees with test outcomes')
        results[suite] = found
    return results


def verify_authentication_tests(value):
    if not isinstance(value, dict) or set(value) != set(AUTHENTICATION_TESTS):
        raise ValueError('missing required authentication suites')
    for suite, methods in AUTHENTICATION_TESTS.items():
        if not isinstance(value[suite], dict) or set(value[suite]) != methods or any(
                status != 'PASS' for status in value[suite].values()):
            raise ValueError('authentication regression missing, failed or skipped')


def verify_build(value):
    if value['sourceSha'] != git_sha() or value['harnessSha'] != harness_sha():
        raise ValueError('business build belongs to a different checkout')
    verify_authentication_tests(value.get('authenticationTests'))
    jars = {s: digest(ROOT / f'apps/server/{s}-service/target/{s}-service-0.0.1-SNAPSHOT.jar') for s in SERVICES}
    web = {str(p.relative_to(ROOT / 'apps/web/dist')): digest(p)
           for p in sorted((ROOT / 'apps/web/dist').rglob('*')) if p.is_file()}
    if value['jars'] != jars or value['web'] != web or not web:
        raise ValueError('business artifact changed after preparation')


def verify_live(value, context):
    if set(value) != set(LIVE_CASES):
        raise ValueError('missing or extra live business case')
    request_ids = []
    for key, row in value.items():
        request_ids.append(row['requestId'])
        if row['problemVersionId'] != context['problemVersionId']:
            raise ValueError('live result version mismatch')
        if key in STATUSES:
            if row['status'] != STATUSES[key] or row['problemId'] != context['problemId']:
                raise ValueError('custom-run outcome mismatch')
            if row['effectiveLimits']['cpuNs'] != 1000000000 or row['effectiveLimits']['memoryBytes'] != 268435456:
                raise ValueError('custom-run limits changed')
            if not 0 < row['httpNs'] < 60_000_000_000 or not 0 < row['bodyBytes'] < 200000:
                raise ValueError('HTTP observation exceeded bounds')
            if key != 'ce' and (not isinstance(row['cpuNs'], int) or not isinstance(row['memoryBytes'], int)):
                raise ValueError('missing actual resource measurements')
    if any(not isinstance(v, str) or not v or len(v) > 128 for v in request_ids) or len(set(request_ids)) != len(request_ids):
        raise ValueError('missing or duplicate request identities')
    if value['output']['stdoutTruncated'] is not True or value['output']['stdoutBytes'] > 1048576:
        raise ValueError('unbounded output')
    if value['empty']['memoryBytes'] >= 16 << 20:
        raise ValueError('empty run inherited a prior memory peak')
    if value['ac']['verdict'] != 'AC' or value['ac']['passedCount'] != 6 or value['ac']['totalCount'] != 6 or value['wa']['verdict'] != 'WA':
        raise ValueError('formal verdict/count mismatch')
    if value['history']['draftPreserved'] is not True or value['history']['customPosts'] != 8 or value['history']['formalPosts'] != 2:
        raise ValueError('history changed draft or caused extra execution')
