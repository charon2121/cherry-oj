#!/usr/bin/env python3
"""Render reviewable configuration without touching system paths or services."""
import argparse
import json
from pathlib import Path
import re
import shutil
from urllib.parse import urlsplit

from layout import ACCOUNTS, BUDGETS, ETC, GROUP, STATE, UNITS


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
    for name in ('helper-start.py', 'health.py'):
        shutil.copyfile(source / name, output / name)
    root = STATE / 'releases' / release
    helper = dict(SocketPath='/run/cherry-sandbox-helper/helper.sock',
                  StateDir='/run/cherry-sandbox-helper', JobsDir=GROUP+'/cherry-sandbox-helper.service/jobs',
                  RootFS=str(root/'rootfs'), ManifestPath=str(root/'manifest.json'),
                  ManifestSHA256=manifest_hash, ServiceUID=61001, ServiceGID=61001,
                  PayloadUID=61002, PayloadGID=61002, InitUID=61003, InitGID=61003, Parallelism=1)
    sandbox = dict(logging=dict(path=str(STATE/'service/logs')), sandbox=dict(
        backend='linux', httpAddr='127.0.0.1:15050', helperSocket=helper['SocketPath'],
        workspaceRoot=str(STATE/'service/work'), parallelism=1, queueSize=4,
        maxRequestBytes=2 << 20, store=dict(root=str(STATE/'service/blobs'), maxBlobBytes=64 << 20,
        maxTotalBytes=256 << 20, maxEntries=128, retention='1h')))
    judge = dict(logging=dict(path=str(STATE/'judge/logs')), judge=dict(
        httpAddr='127.0.0.1:15051', sandboxURL='http://127.0.0.1:15050',
        testdataRoot=str(STATE/'judge/testdata'), sandboxTimeout='60s',
        compile=dict(cpuNs=10_000_000_000, memoryBytes=256 << 20, clockNs=20_000_000_000),
        node=dict(enabled=True, id=node_id, controlPlaneURL=control_url,
                  advertiseURL=advertise_url, deploymentManifest=str(ETC/'deployment.json'),
                  controlToken='REPLACE_FROM_PRIVATE_TOKEN_FILE')))
    write_json(output/'helper.json', helper)
    write_json(output/'sandbox.json', sandbox)
    write_json(output/'judge.json', judge, 0o600)
    write_json(output/'plan.json', dict(version=1, release=release, nodeID=node_id,
        accounts=ACCOUNTS, units=list(UNITS), rootfsSHA256=manifest_hash,
        directories=[str(ETC),str(STATE),'/run/cherry-sandbox-helper'],
        controlURL=control_url, advertiseURL=advertise_url,
        startAutomatically=False, deleteUserData=False, reboot=False))
    files = {'sandbox': root/'bin/sandbox', 'helper': root/'bin/sandbox-helper',
             'rootfsManifest': root/'manifest.json', 'toolchainLock': root/'packages.lock.json',
             'helperConfig': ETC/'helper.json', 'sandboxConfig': ETC/'sandbox.json',
             'bootstrap': ETC/'helper-start.py'}
    for key, name in zip(('slice','helperUnit','sandboxUnit','judgeUnit'),UNITS):
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
