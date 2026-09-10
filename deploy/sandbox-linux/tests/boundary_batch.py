#!/usr/bin/env python3
"""Run explicit boundary integration tests inside one owned delegated unit."""
import os
from pathlib import Path
import sys

base = Path(sys.argv[1])
assert base.parent == Path('/var/lib/cherry-sandbox-test')
assert base.name.startswith('work048-boundary-') and not base.is_symlink()
relative = Path('/proc/self/cgroup').read_text().strip().split('::', 1)[1]
group = Path('/sys/fs/cgroup' + relative)
assert group.name == 'cherry-sandbox-test-' + base.name + '.service'
(group / 'supervisor').mkdir()
(group / 'supervisor/cgroup.procs').write_text(str(os.getpid()))
(group / 'cgroup.subtree_control').write_text('+cpu +memory +pids')
for name, value in {'memory.max': str(128 << 20), 'memory.swap.max': '0', 'pids.max': '16', 'cpu.max': '50000 100000'}.items():
    path = group / 'supervisor' / name
    path.write_text(value)
    assert path.read_text().strip() == value
(group / 'jobs').mkdir()
(group / 'jobs/cgroup.subtree_control').write_text('+cpu +memory +pids')
os.environ['CHERRY_BOUNDARY_BASE'] = str(base)
os.environ['CHERRY_BOUNDARY_JOBS'] = str(group / 'jobs')
os.execv(str(base / 'boundary.test'), [str(base / 'boundary.test'), '-test.v', '-test.timeout=45s'])
