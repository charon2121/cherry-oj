"""Own Docker resources before creation; never adopt or prune other workloads."""
import json
from pathlib import Path
import re
import stat

from command import run
from owned import BASE, github_vm
from report import read_json

RECORD = BASE / '.business-owner.json'
LABEL = 'org.cherry-oj.ci-business'


class Dependencies:
    def __init__(self, private, output):
        self.private, self.output, self.identity = Path(private), Path(output), github_vm()
        self.counter = 0
        self.data = dict(run=self.identity, containers=[], volumes=[])
        self.save()

    @classmethod
    def load(cls, private, output):
        obj = cls.__new__(cls)
        obj.private, obj.output, obj.identity = Path(private), Path(output), github_vm()
        info = RECORD.lstat()
        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_uid != 0 or info.st_mode & 0o077:
            raise ValueError('untrusted business ownership record')
        obj.counter, obj.data = 0, read_json(RECORD)
        if set(obj.data) != {'run', 'containers', 'volumes'} or obj.data['run'] != obj.identity:
            raise ValueError('foreign business ownership record')
        for kind in ('containers', 'volumes'):
            names = obj.data[kind]
            if len(names) > 3 or len(set(names)) != len(names) or any(
                    not re.fullmatch('cherry-ci-' + re.escape(obj.identity) + '-(mysql|redis|kafka)', n) for n in names):
                raise ValueError('invalid business resource names')
        return obj

    def save(self):
        temp = RECORD.with_suffix('.pending')
        temp.write_text(json.dumps(self.data))
        temp.chmod(0o600)
        temp.replace(RECORD)

    def docker(self, *argv, timeout=30):
        # Docker inspect may contain secrets. Keep all raw CLI output in the private tree.
        self.counter += 1
        log = self.private / f'docker-{self.counter}.log'
        try:
            run(['docker', *argv], log, timeout)
            if log.stat().st_size > (1 << 20):
                raise ValueError('docker response exceeds bound')
            return log.read_text().strip()
        finally:
            log.unlink(missing_ok=True)

    def exists(self, kind, name):
        found = self.docker(kind, 'ls', '--format', '{{.Names}}' if kind == 'container' else '{{.Name}}',
                            *(['--all'] if kind == 'container' else []))
        return name in found.splitlines()

    def claim(self, kind, name):
        if self.exists(kind, name):
            raise RuntimeError('refusing existing Docker resource')
        self.data[kind + 's'].append(name)
        self.save()

    def start(self, key, image, args, *, memory):
        name = 'cherry-ci-' + self.identity + '-' + key
        self.docker('pull', image, timeout=180)
        self.claim('volume', name)
        self.docker('volume', 'create', '--label', LABEL + '=' + self.identity, name)
        self.claim('container', name)
        target = {'mysql': '/var/lib/mysql', 'redis': '/data', 'kafka': '/mnt/shared/config'}[key]
        self.docker('run', '--detach', '--name', name, '--label', LABEL + '=' + self.identity,
                    '--memory', str(memory) + 'm', '--memory-swap', str(memory) + 'm', '--pids-limit', '256',
                    '--cpus', '1', '--log-driver', 'local', '--log-opt', 'max-size=1m', '--log-opt', 'max-file=2',
                    '--mount', f'type=volume,src={name},dst={target}', *args, timeout=120)
        metadata = json.loads(self.docker('inspect', '--format',
                              '{"id":{{json .Id}},"image":{{json .Image}},"memory":{{.HostConfig.Memory}},'
                              '"memorySwap":{{.HostConfig.MemorySwap}},"pids":{{.HostConfig.PidsLimit}},"nanoCpus":{{.HostConfig.NanoCpus}}}', name))
        if metadata['memory'] != memory << 20 or metadata['memorySwap'] != memory << 20 or metadata['pids'] != 256 or metadata['nanoCpus'] != 1000000000:
            raise ValueError('dependency limits mismatch')
        metadata['reference'] = image
        (self.output / (key + '-container.json')).write_text(json.dumps(metadata) + '\n')
        return name

    def diagnose(self):
        states = {}
        for name in self.data['containers']:
            if self.exists('container', name):
                states[name] = json.loads(self.docker('container', 'inspect', '--format',
                    '{"status":{{json .State.Status}},"exitCode":{{.State.ExitCode}},"oomKilled":{{.State.OOMKilled}}}', name))
        (self.output / 'dependency-states.json').write_text(json.dumps(states) + '\n')

    def cleanup(self):
        # Validate every resource before removing any. Labels must match this invocation.
        for kind in ('container', 'volume'):
            for name in self.data[kind + 's']:
                if self.exists(kind, name):
                    value = self.docker(kind, 'inspect', '--format',
                                        '{{index ' + ('.Config.Labels' if kind == 'container' else '.Labels') +
                                        ' "' + LABEL + '"}}', name)
                    if value != self.identity:
                        raise RuntimeError('Docker ownership changed; retaining resources')
        for name in reversed(self.data['containers']):
            if self.exists('container', name):
                self.docker('container', 'rm', '--force', '--volumes', name)
        for name in reversed(self.data['volumes']):
            if self.exists('volume', name):
                self.docker('volume', 'rm', name)
        remaining = {kind: self.docker(kind, 'ls', '--filter', 'label=' + LABEL + '=' + self.identity,
                                     '--quiet', *(['--all'] if kind == 'container' else [])).splitlines()
                     for kind in ('container', 'volume')}
        # run() writes a sentinel for commands without output; filter only that known sentinel.
        remaining = {key: [v for v in values if v != 'Command completed with exit status 0.']
                     for key, values in remaining.items()}
        (self.output / 'dependencies-after.json').write_text(json.dumps(remaining) + '\n')
        if any(remaining.values()):
            raise RuntimeError('owned Docker resources survived cleanup')
