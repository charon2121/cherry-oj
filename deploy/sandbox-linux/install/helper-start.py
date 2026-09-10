#!/usr/bin/env python3
"""Prepare only the helper unit's delegated tree, then exec the root-owned binary."""
import json
import os
from pathlib import Path

CONFIG = Path('/etc/cherry-sandbox/helper.json')
GROUP = Path('/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/cherry-sandbox-helper.service')


def main():
    config = json.loads(CONFIG.read_text())
    expected = str(GROUP).removeprefix('/sys/fs/cgroup')
    actual = Path('/proc/self/cgroup').read_text().strip()
    if actual != '0::' + expected or config['JobsDir'] != str(GROUP / 'jobs'):
        raise RuntimeError('unexpected cgroup owner')
    # Startup is single-threaded; moving ourselves leaves the delegation root empty.
    (GROUP / 'supervisor').mkdir(exist_ok=True)
    (GROUP / 'supervisor/cgroup.procs').write_text(str(os.getpid()))
    (GROUP / 'cgroup.subtree_control').write_text('+cpu +memory +pids')
    (GROUP / 'jobs').mkdir(exist_ok=True)
    (GROUP / 'jobs/cgroup.subtree_control').write_text('+cpu +memory +pids')
    for name, value in {'memory.max': str(128 << 20), 'memory.swap.max': '0',
                        'pids.max': '32', 'cpu.max': '50000 100000'}.items():
        (GROUP / 'supervisor' / name).write_text(value)
    for name, value in {'memory.max': str(640 << 20), 'memory.swap.max': '0',
                        'pids.max': '160', 'cpu.max': '100000 100000',
                        'memory.oom.group': '1'}.items():
        (GROUP / 'jobs' / name).write_text(value)
    binary = '/var/lib/cherry-sandbox/current/bin/sandbox-helper'
    os.execv(binary, [binary, '--config', str(CONFIG)])


if __name__ == '__main__':
    main()
