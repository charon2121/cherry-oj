"""Ownership and teardown of a fresh native install in an exclusive disposable VM."""
import grp
import json
import os
from pathlib import Path
import pwd
import shutil
import stat
import subprocess
import sys
import time

from owned import BASE, Owned, github_vm
from report import ROOT, digest, read_json

sys.path.append(str(ROOT / 'deploy/sandbox-linux/install'))
from layout import ACCOUNTS, ETC, GROUP, STATE, UNITS

SYSTEMD = Path('/etc/systemd/system')
RUNTIME = Path('/run/cherry-sandbox-helper')
DROPINS = Path('/run/systemd/system/cherry-sandbox-helper.service.d')
CAPS = ('CAP_SYS_ADMIN', 'CAP_SETUID', 'CAP_SETGID', 'CAP_SETPCAP',
        'CAP_CHOWN', 'CAP_DAC_OVERRIDE', 'CAP_MKNOD')
RECORD = BASE / '.native-owner.json'


def capability_content(caps):
    value = '[Service]\nCapabilityBoundingSet=\nCapabilityBoundingSet=' + ' '.join(caps)
    value += '\nAmbientCapabilities=\n'
    if 'CAP_SETUID' in caps:
        value += 'AmbientCapabilities=CAP_SETUID\n'
    return value


def regular(path):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_uid != 0 or info.st_mode & 0o022:
        raise RuntimeError('untrusted native ownership file: ' + str(path))


def properties(unit):
    raw = subprocess.check_output(['systemctl', 'show', unit, '-p', 'FragmentPath', '-p', 'DropInPaths',
                                   '-p', 'UnitFileState'], text=True, timeout=5)
    return dict(line.split('=', 1) for line in raw.splitlines())


def resources():
    tasks, mounts = [], []
    for proc in Path('/proc').glob('[0-9]*'):
        try:
            rows = dict(line.split(':', 1) for line in (proc / 'status').read_text().splitlines() if ':' in line)
            ids = {int(v) for key in ('Uid', 'Gid') for v in rows[key].split()}
            if ids & set(ACCOUNTS.values()):
                tasks.append(int(proc.name))
            for line in (proc / 'mountinfo').read_text().splitlines():
                if str(STATE) + '/' in line or str(RUNTIME) in line:
                    mounts.append(dict(pid=int(proc.name), mount=line))
        except (FileNotFoundError, ProcessLookupError):
            continue
    group = Path(GROUP)
    return dict(tasks=tasks, mounts=mounts, cgroups=[str(group)] if group.exists() else [])


class Installation:
    def __init__(self, output, review):
        self.output = Path(output)
        # Preflight proved absence. Persist claims BEFORE the first install operation, so partial
        # installs can be cleaned without adopting an older receipt or depending on its last write.
        self.data = dict(run=github_vm(), units={unit: digest(review / unit) for unit in UNITS})
        unit_paths = [SYSTEMD / unit for unit in UNITS]
        dropin_paths = [parent / (unit + '.d') for parent in (SYSTEMD, Path('/run/systemd/system')) for unit in UNITS]
        if any(p.exists() or p.is_symlink() for p in (ETC, STATE, RUNTIME, RECORD, *unit_paths, *dropin_paths)):
            raise RuntimeError('native resources appeared after preflight')
        for name, uid in ACCOUNTS.items():
            for lookup, key in ((pwd.getpwnam, name), (pwd.getpwuid, uid), (grp.getgrnam, name), (grp.getgrgid, uid)):
                try:
                    lookup(key)
                except KeyError:
                    continue
                raise RuntimeError('native identity appeared after preflight')
        with RECORD.open('x') as stream:
            json.dump(self.data, stream)
        RECORD.chmod(0o600)

    @classmethod
    def load(cls, output):
        Owned.load(output)  # Also verifies the enclosing run marker.
        regular(RECORD)
        data = read_json(RECORD)
        if set(data) != {'run', 'units'} or data['run'] != github_vm() or set(data['units']) != set(UNITS):
            raise RuntimeError('native resources belong to another run')
        obj = cls.__new__(cls)
        obj.output, obj.data = Path(output), data
        return obj

    def verify_units(self):
        for unit, expected in self.data['units'].items():
            path = SYSTEMD / unit
            if path.exists() or path.is_symlink():
                regular(path)
                if digest(path) != expected:
                    raise RuntimeError('installed unit changed; refusing cleanup: ' + unit)
            facts = properties(unit)
            if facts.get('FragmentPath', '') not in ('', str(path)) or facts.get('UnitFileState') in ('enabled', 'enabled-runtime'):
                raise RuntimeError('unit ownership or enable state changed: ' + unit)
            allowed = str(DROPINS / '90-work048-capability-test.conf') if unit == UNITS[1] else ''
            if facts.get('DropInPaths', '') not in ('', allowed):
                raise RuntimeError('unknown native unit drop-in')
        if DROPINS.exists() or DROPINS.is_symlink():
            if DROPINS.is_symlink() or not {p.name for p in DROPINS.iterdir()} <= {'90-work048-capability-test.conf'}:
                raise RuntimeError('unknown capability test directory')
            path = DROPINS / '90-work048-capability-test.conf'
            if path.exists() or path.is_symlink():
                regular(path)
                allowed = {capability_content(CAPS)} | {capability_content(tuple(c for c in CAPS if c != excluded)) for excluded in CAPS}
                if path.read_text() not in allowed:
                    raise RuntimeError('capability test drop-in changed')

    def cleanup(self):
        self.verify_units()
        # Drivers must already be stopped: their finally blocks otherwise race this teardown.
        for unit in reversed(UNITS):
            subprocess.run(['systemctl', 'stop', unit], stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL, timeout=20, check=False)
        deadline = time.monotonic() + 5
        while True:
            after = resources()
            if not any(after.values()) or time.monotonic() >= deadline:
                break
            time.sleep(.05)
        (self.output / 'native-resources-stopped.json').write_text(json.dumps(after, indent=2) + '\n')
        if any(after.values()):
            raise RuntimeError('native tasks, mounts or cgroups remain; refusing file removal')
        self.verify_units()
        for unit in UNITS:
            path = SYSTEMD / unit
            if path.exists():
                path.unlink()
        if DROPINS.exists():
            (DROPINS / '90-work048-capability-test.conf').unlink(missing_ok=True)
            DROPINS.rmdir()
        subprocess.run(['systemctl', 'daemon-reload'], check=True, timeout=15)
        # Only the exact names/IDs absent before this invocation can be removed; never userdel -r.
        for name, uid in ACCOUNTS.items():
            try:
                account = pwd.getpwnam(name)
            except KeyError:
                account = None
            if account:
                if (account.pw_uid, account.pw_gid, account.pw_dir, account.pw_shell) != (uid, uid, '/nonexistent', '/usr/sbin/nologin'):
                    raise RuntimeError('native account changed: ' + name)
                subprocess.run(['userdel', name], check=True, timeout=10, stdout=subprocess.DEVNULL)
            try:
                group = grp.getgrnam(name)
            except KeyError:
                group = None
            if group:
                if group.gr_gid != uid or group.gr_mem:
                    raise RuntimeError('native group changed: ' + name)
                subprocess.run(['groupdel', name], check=True, timeout=10, stdout=subprocess.DEVNULL)
        for path in (ETC, STATE, RUNTIME):
            if path.is_symlink():
                raise RuntimeError('owned native root changed to symlink')
            if path.exists():
                info = path.stat()
                if info.st_uid != 0 or info.st_mode & 0o022:
                    raise RuntimeError('owned native root is not protected')
                # Python's fd-based rmtree does not follow payload-controlled links.
                if not shutil.rmtree.avoids_symlink_attacks:
                    raise RuntimeError('safe recursive removal unavailable')
                shutil.rmtree(path)
        after = resources()
        after['paths'] = [str(p) for p in (ETC, STATE, RUNTIME, DROPINS, *(SYSTEMD / u for u in UNITS)) if p.exists() or p.is_symlink()]
        after['accounts'] = [p.pw_name for p in pwd.getpwall() if p.pw_uid in ACCOUNTS.values() or p.pw_name in ACCOUNTS]
        after['groups'] = [g.gr_name for g in grp.getgrall() if g.gr_gid in ACCOUNTS.values() or g.gr_name in ACCOUNTS]
        (self.output / 'native-resources-after.json').write_text(json.dumps(after, indent=2) + '\n')
        if any(after.values()):
            raise RuntimeError('native cleanup incomplete')
        RECORD.unlink()
