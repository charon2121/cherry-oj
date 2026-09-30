#!/usr/bin/env python3
"""Render reviewable configuration without touching system paths or services."""
import argparse
import json
from pathlib import Path
import re
import shutil
from urllib.parse import urlsplit

from layout import (ACCOUNTS, BLOBS_ROOT, BOXES_ROOT, BUDGETS, ETC, EXECUTOR, GROUP, INIT_UID, JUDGE_DATA,
                    JUDGE_GROUP, JUDGE_UID, JUDGE_UNIT, PAYLOAD_UID, STATE, UNITS)


def write_json(path, value, mode=0o644):
    path.write_text(json.dumps(value, indent=2) + '\n')
    path.chmod(mode)


def origin(value):
    parsed = urlsplit(value)
    if (parsed.scheme not in ('http', 'https') or not parsed.hostname or
            parsed.username or parsed.password or parsed.path not in ('', '/') or
            parsed.query or parsed.fragment):
        raise ValueError('control/advertise URL must be an HTTP(S) origin')
    # Initial deployment only permits tunnel endpoints; never exposes judge publicly.
    if parsed.hostname != '127.0.0.1':
        raise ValueError('initial deployment requires explicit loopback tunnel endpoints')
    return value


def render(output, release, node_id, control_url, advertise_url, manifest_hash):
    if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}', release):
        raise ValueError('invalid release')
    if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}', node_id):
        raise ValueError('invalid node id')
    if not re.fullmatch('[0-9a-f]{64}', manifest_hash):
        raise ValueError('rootfs manifest SHA256 required')
    control_url, advertise_url = origin(control_url), origin(advertise_url)
    output.mkdir(mode=0o700)
    source = Path(__file__).resolve().parent
    for name in UNITS:
        shutil.copyfile(source.parent / 'systemd' / name, output / name)
    for name in ('judge-start.py', 'health.py'):
        shutil.copyfile(source / name, output / name)
    root = STATE / 'releases' / release
    # 每次执行占用一个 box；执行层的并发数与执行器的 box 数从这一个值渲染，不能各写各的。
    parallelism = 1
    executor = '\n'.join([
        'rootfs=' + str(root / 'rootfs'), 'boxes=' + str(BOXES_ROOT), 'cgroup=' + JUDGE_GROUP + '/jobs',
        'box_count=' + str(parallelism), f'service_uid={JUDGE_UID}', f'service_gid={JUDGE_UID}',
        f'payload_uid={PAYLOAD_UID}', f'payload_gid={PAYLOAD_UID}', f'init_uid={INIT_UID}', f'init_gid={INIT_UID}']) + '\n'
    (output / 'executor.conf').write_text(executor)
    (output / 'executor.conf').chmod(0o644)
    def limits(suffix, oom_group=False):
        memory, pids, cpu = BUDGETS[suffix]
        values = {'memory.max': str(memory), 'memory.swap.max': '0', 'pids.max': str(pids), 'cpu.max': cpu}
        if oom_group:
            values['memory.oom.group'] = '1'
        return values
    start = dict(group=JUDGE_GROUP, rootfs=str(root / 'rootfs'), manifest=str(root / 'manifest.json'),
                 manifestSha256=manifest_hash, supervisor=limits('/' + JUDGE_UNIT + '/supervisor'),
                 jobs=limits('/' + JUDGE_UNIT + '/jobs', oom_group=True),
                 command=[str(STATE / 'current/bin/judge'), '-config', str(ETC / 'judge.json')])
    judge = dict(logging=dict(path=str(JUDGE_DATA / 'logs')), judge=dict(
        httpAddr='127.0.0.1:15051', sandboxMode='local',
        testdataRoot=str(JUDGE_DATA / 'testdata'), sandboxTimeout='60s',
        compile=dict(cpuNs=10_000_000_000, memoryBytes=256 << 20, clockNs=20_000_000_000),
        node=dict(enabled=True, id=node_id, controlPlaneURL=control_url,
                  advertiseURL=advertise_url, deploymentManifest=str(ETC/'deployment.json'),
                  controlToken='REPLACE_FROM_PRIVATE_TOKEN_FILE')),
        execution=dict(backend='linux', executorPath=str(EXECUTOR), boxesRoot=str(BOXES_ROOT),
                       parallelism=parallelism, queueSize=4,
                       store=dict(root=str(BLOBS_ROOT), maxBlobBytes=64 << 20, maxTotalBytes=256 << 20,
                                  maxEntries=128, retention='1h')))
    write_json(output/'judge-start.json', start)
    write_json(output/'judge.json', judge, 0o600)
    write_json(output/'plan.json', dict(version=1, release=release, nodeID=node_id,
        accounts=ACCOUNTS, units=list(UNITS), rootfsSHA256=manifest_hash,
        directories=[str(ETC),str(STATE)],
        controlURL=control_url, advertiseURL=advertise_url,
        startAutomatically=False, deleteUserData=False, reboot=False))
    files = {'executor': root/'libexec/sandbox',
             'rootfsManifest': root/'manifest.json', 'toolchainLock': root/'packages.lock.json',
             'executorConfig': ETC/'executor.conf', 'startConfig': ETC/'judge-start.json',
             'bootstrap': ETC/'judge-start.py'}
    for key, name in zip(('slice','judgeUnit'),UNITS):
        files[key] = Path('/etc/systemd/system')/name
    # Release hashes are filled from the supplied immutable release by the installer.
    write_json(output/'deployment.template.json', dict(version=1,backend='linux',architecture='amd64',
        files={key:dict(path=str(path),sha256='') for key,path in files.items()},
        limits={GROUP+unit+'/'+name:value for unit,(memory,pids,cpu) in BUDGETS.items()
                for name,value in {'memory.max':str(memory),'memory.swap.max':'0',
                                   'pids.max':str(pids),'cpu.max':cpu}.items()}))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--release', required=True)
    parser.add_argument('--node-id', required=True)
    parser.add_argument('--control-url', required=True)
    parser.add_argument('--advertise-url', required=True)
    parser.add_argument('--manifest-sha256', required=True)
    args=parser.parse_args()
    render(args.output,args.release,args.node_id,args.control_url,args.advertise_url,args.manifest_sha256)
    print('Rendered reviewable plan; no accounts, system files or services changed.')


if __name__ == '__main__':
    main()
