"""Translate the native verification scripts' actual assertions into required CI cases."""
import re
from native_resources import CAPS
from results import json_lines, markers


def check(kind, path, fingerprint):
    rows = json_lines(path)
    if kind == 'native':
        if len(rows) != 1:
            raise ValueError('missing or duplicate native execution result')
        row = rows[0]
        expected = dict(result='PASS', taskUIDs=[61002, 61003], capabilities=0, noNewPrivileges=1,
                        namespaces=6, readOnlyMounts=3, verifiedLimits=24)
        if any(row.get(k) != v for k, v in expected.items()):
            raise ValueError('native isolation result incomplete')
        if row['taskThreads'] <= 0 or set(row['serviceThreads']) != {'cherry-sandbox', 'cherry-sandbox-judge'} or any(v <= 0 for v in row['serviceThreads'].values()):
            raise ValueError('missing native thread observations')
        if any(row.get(k, 0) <= 0 for k in ('compileCpuNs', 'compileMemoryBytes', 'runCpuNs', 'runMemoryBytes')):
            raise ValueError('missing native resource measurements')
        markers(path, sentinel='No task processes, execution cgroups or workspace files remain.')
    elif kind in ('helper-config', 'rootfs-manifest', 'helper-binary'):
        if len(rows) != 1 or rows[0].get('case') != kind or rows[0].get('result') != 'PASS':
            raise ValueError('missing lifecycle refusal')
        markers(path, sentinel='Original files/identity restored; all three services healthy.')
    elif kind.startswith('kill-'):
        if len(rows) != 1:
            raise ValueError('missing or duplicate service fault result')
        row = rows[0]
        if row.get('test') != kind[5:] or row.get('result') != 'PASS' or row.get('recovered') != 'RAN' or row.get('fingerprint') != fingerprint:
            raise ValueError('service fault recovery identity mismatch')
        if row.get('killedPID', 0) <= 1 or row.get('observedPayload', 0) <= 1 or not row.get('requestOutcome'):
            raise ValueError('missing actual in-flight kill observation')
    elif kind == 'caps':
        baseline = [r for r in rows if r.get('test') == 'seven-capability-set']
        removed = [r for r in rows if r.get('test') == 'remove-capability']
        expected = dict(test='seven-capability-set', result='PASS', nestedInput=True, privateOutput=True, descendantsReaped=True)
        if len(rows) != 8 or len(baseline) != 1 or any(baseline[0].get(k) != v for k, v in expected.items()):
            raise ValueError('missing seven-capability positive control')
        if len(removed) != 7 or {r.get('removed') for r in removed} != set(CAPS) or any(r.get('startup') != 'REFUSED' for r in removed):
            raise ValueError('missing capability deletion negative control')
        invocations = [r.get('invocationID', '') for r in rows]
        times = [r.get('mainStartedNs', 0) for r in rows]
        if (any(not re.fullmatch('[0-9a-f]{32}', value) for value in invocations) or len(set(invocations)) != 8
                or any(not isinstance(value, int) or value <= 0 for value in times)
                or times != sorted(set(times))):
            raise ValueError('capability refusal without eight actual distinct starts')
        markers(path, sentinel='Original deployment restored; no judge registration under temporary policy.')
    elif kind == 'uninstall':
        markers(path, sentinel='PASS: only recorded units removed; accounts, configurations and test data retained.')
        markers(path, sentinel='PASS: exact original units restored, all services active, no enable and unchanged manifest.')
    else:
        raise ValueError('unknown native case')
