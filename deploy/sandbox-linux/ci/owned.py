"""Exclusive resources for the kernel suite. Never adopts an existing test installation."""
import grp
import json
import os
from pathlib import Path
import platform
import pwd
import re
import shutil
import socket
import subprocess
import time

from command import run

BASE = Path('/var/lib/cherry-sandbox-test')
CGROUP = Path('/sys/fs/cgroup/system.slice')
MARKER = BASE / '.ci-owner.json'
PREFIX = 'cherry-sandbox-test-work048-'


def github_vm():
    if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        raise RuntimeError('privileged suite requires an ephemeral GitHub-hosted VM')
    if os.geteuid() != 0 or platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise RuntimeError('root on Linux/amd64 required')
    run_id = os.environ.get('GITHUB_RUN_ID', '')
    attempt = os.environ.get('GITHUB_RUN_ATTEMPT', '')
    if not re.fullmatch('[0-9]+', run_id) or not re.fullmatch('[0-9]+', attempt):
        raise RuntimeError('missing GitHub execution identity')
    return run_id + '-' + attempt


def snapshot():
    tasks, mounts = [], []
    for proc in Path('/proc').glob('[0-9]*'):
        try:
            status = dict(line.split(':', 1) for line in (proc / 'status').read_text().splitlines() if ':' in line)
            uids = [int(v) for v in status['Uid'].split()]
            if any(61001 <= uid <= 61010 for uid in uids):
                tasks.append(dict(pid=int(proc.name), uids=uids, state=status['State'].strip(),
                                  cgroup=(proc / 'cgroup').read_text().strip()))
            # Inspect every surviving mount namespace, including namespaces different from this driver.
            for line in (proc / 'mountinfo').read_text().splitlines():
                if str(BASE) in line or '/run/' + PREFIX in line:
                    mounts.append(dict(pid=int(proc.name), mount=line))
        except (FileNotFoundError, ProcessLookupError):
            continue
    groups = sorted(str(p) for p in CGROUP.glob(PREFIX + '*.service'))
    return dict(tasks=tasks, mounts=mounts, cgroups=groups)


def preflight():
    github_vm()
    if Path('/proc/1/comm').read_text().strip() != 'systemd':
        raise RuntimeError('systemd host required')
    if BASE.exists() or BASE.is_symlink():
        raise RuntimeError('existing fixture directory; refusing adoption')
    for name in ('/etc/cherry-sandbox', '/var/lib/cherry-sandbox', '/run/cherry-sandbox-helper'):
        if Path(name).exists() or Path(name).is_symlink():
            raise RuntimeError('existing native sandbox installation')
    for uid in range(61001, 61011):
        for lookup in (pwd.getpwuid, grp.getgrgid):
            try:
                lookup(uid)
            except KeyError:
                continue
            raise RuntimeError('test UID/GID already allocated: ' + str(uid))
    before = snapshot()
    if any(before.values()):
        raise RuntimeError('existing test processes/mounts/cgroups')
    if list(Path('/run').glob('cherry-sandbox-test-*')):
        raise RuntimeError('existing runtime test resources')
    units = subprocess.check_output(['systemctl', 'list-units', '--all', '--no-legend',
                                     'cherry-sandbox*'], text=True, timeout=5)
    if units.strip():
        raise RuntimeError('existing sandbox unit')
    for port in (15050, 15051):
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', port))
    required = ['cgroup.controllers', 'memory.peak', 'cgroup.kill', 'cpu.stat',
                'memory.max', 'memory.swap.max', 'memory.oom.group', 'pids.max']
    for name in required:
        if not (CGROUP / name).exists():
            raise RuntimeError('missing cgroup v2 interface: ' + name)
    if not {'cpu', 'memory', 'pids'} <= set((CGROUP / 'cgroup.controllers').read_text().split()):
        raise RuntimeError('missing resource controller')
    return dict(system=platform.system(), kernel=platform.release(), architecture=platform.machine(),
                osRelease=Path('/etc/os-release').read_text(), cpuCount=os.cpu_count(),
                memoryInfo=Path('/proc/meminfo').read_text(),
                lsm=Path('/sys/kernel/security/lsm').read_text().strip(),
                runnerImage=os.environ.get('ImageVersion', 'unknown'), before=before)


class Owned:
    def __init__(self, output):
        self.output = Path(output)
        self.identity = github_vm()
        BASE.mkdir(mode=0o755)
        self.data = dict(run=self.identity, units=[])
        self.save()

    @classmethod
    def load(cls, output):
        obj = cls.__new__(cls)
        obj.output, obj.identity = Path(output), github_vm()
        if BASE.is_symlink() or MARKER.is_symlink() or MARKER.stat().st_uid != 0 or MARKER.stat().st_mode & 0o022:
            raise RuntimeError('untrusted ownership marker')
        obj.data = json.loads(MARKER.read_text())
        if set(obj.data) != {'run', 'units'} or obj.data['run'] != obj.identity:
            raise RuntimeError('resource belongs to another run')
        units = obj.data['units']
        if not isinstance(units, list) or len(units) > 64 or len(units) != len(set(units)):
            raise RuntimeError('invalid owned units')
        if any(not re.fullmatch(PREFIX + '[a-z0-9-]+', unit) or obj.identity not in unit for unit in units):
            raise RuntimeError('unit outside this run')
        return obj

    def save(self):
        path = MARKER.with_suffix('.pending')
        path.write_text(json.dumps(self.data))
        path.chmod(0o600)
        path.replace(MARKER)

    def register(self, *units):
        for unit in units:
            if not re.fullmatch(PREFIX + '[a-z0-9-]+', unit) or self.identity not in unit:
                raise ValueError('unit outside this run')
            if unit in self.data['units']:
                raise ValueError('unit already registered')
            self.data['units'].append(unit)
        self.save()  # Register before starting, including child units started by the old drivers.

    def launch(self, unit, args, log, *, memory=128, tasks=32, seconds=90, delegate=False, wait=True):
        command = ['systemd-run', '--unit=' + unit, '--collect']
        if wait:
            command += ['--wait', '--pipe']
        properties = [f'MemoryMax={memory}M', 'MemorySwapMax=0', f'TasksMax={tasks}',
                      'CPUQuota=100%' if delegate else 'CPUQuota=50%', f'RuntimeMaxSec={seconds}',
                      'KillMode=control-group', 'TimeoutStopSec=3s']
        if delegate:
            properties.append('Delegate=yes')
        for value in properties:
            command += ['-p', value]
        run(command + [str(a) for a in args], self.output / log, seconds + 10 if wait else 10)

    def stop(self, *units):
        if any(unit not in self.data['units'] for unit in units):
            raise ValueError('refusing to stop an unowned unit')
        for unit in units:
            # Collected units may already be gone; the final cgroup/process snapshot decides success.
            subprocess.run(['systemctl', 'stop', unit], stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL, timeout=15, check=False)

    def cleanup(self):
        self.stop(*reversed(self.data['units']))
        deadline = time.monotonic() + 3
        while True:
            after = snapshot()
            if not any(after.values()) or time.monotonic() >= deadline:
                break
            time.sleep(.05)
        (self.output / 'resources-after.json').write_text(json.dumps(after, indent=2) + '\n')
        if any(after.values()):
            raise RuntimeError('surviving process, mount or cgroup; resources retained')
        for unit in self.data['units']:
            path = Path('/run') / unit
            if path.is_symlink():
                raise RuntimeError('runtime path changed to symlink')
            if path.exists():
                shutil.rmtree(path)
        # Every file under BASE originated in this invocation; no mounts/tasks remain to race removal.
        shutil.rmtree(BASE)
