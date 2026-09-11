"""Require every live case, bounded facts, and current build identity before reporting PASS."""

from business_config import SERVICES
from report import ROOT, digest, git_sha, harness_sha

LIVE_CASES = ('io', 'ce', 're', 'signal', 'cpu', 'memory', 'output', 'empty', 'ac', 'wa', 'history')
STATUSES = dict(zip(LIVE_CASES[:8], ('COMPLETED', 'COMPILE_ERROR', 'RUNTIME_ERROR', 'RUNTIME_ERROR',
                                   'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'OUTPUT_LIMIT_EXCEEDED', 'COMPLETED')))


def verify_build(value):
    if value['sourceSha'] != git_sha() or value['harnessSha'] != harness_sha():
        raise ValueError('business build belongs to a different checkout')
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
