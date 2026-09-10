#!/usr/bin/env python3
"""Verify owned unit removal, retained files/accounts, exact restoration and startup."""
import json
import os
from pathlib import Path
import pwd
import sys

sys.path.insert(0, '/var/lib/cherry-sandbox/operations')
import manage


def main():
    before = manage.owned()
    identities = {name: tuple(pwd.getpwnam(name)) for name in manage.ACCOUNTS}
    manifest_hash = manage.digest(manage.ETC / 'deployment.json')
    sentinel = manage.STATE / 'judge/.work048-uninstall-retention'
    with sentinel.open('xb') as stream:
        stream.write(b'WORK-048 retained data check\n')
    sentinel.chmod(0o600)
    os.chown(sentinel, 61010, 61010)
    sentinel_hash = manage.digest(sentinel)
    inode = sentinel.stat().st_ino
    try:
        manage.operate('uninstall')
        assert json.loads(manage.RECEIPT.read_text())['status'] == 'uninstalled'
        assert all(not (manage.SYSTEMD / name).exists() for name in manage.UNITS)
        assert not Path('/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice').exists()
        for path, expected in before['files'].items():
            if path not in {str(manage.SYSTEMD / name) for name in manage.UNITS}:
                assert manage.digest(Path(path)) == expected, path
        assert {name: tuple(pwd.getpwnam(name)) for name in manage.ACCOUNTS} == identities
        assert sentinel.stat().st_ino == inode and manage.digest(sentinel) == sentinel_hash
        print('PASS: only recorded units removed; accounts, configurations and test data retained.', flush=True)
    finally:
        state = json.loads(manage.RECEIPT.read_text())['status']
        if state == 'uninstalled':
            manage.operate('restore')
        manage.operate('start')
        assert manage.digest(manage.ETC / 'deployment.json') == manifest_hash
        assert sentinel.stat().st_ino == inode and manage.digest(sentinel) == sentinel_hash
        sentinel.unlink()
    manage.owned()
    for name in manage.UNITS[1:]:
        assert manage.run('systemctl', 'show', name, '-p', 'ActiveState', '--value') == 'active'
        assert manage.run('systemctl', 'show', name, '-p', 'UnitFileState', '--value') == 'disabled'
    print('PASS: exact original units restored, all services active, no enable and unchanged manifest.', flush=True)


if __name__ == '__main__':
    main()
