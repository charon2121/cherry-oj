#!/usr/bin/env python3
"""持有一棵委派子树，供直接调用执行器的测试使用；只在本次独立、已封顶的 Delegate 单元内运行。

做的事与生产环境的 sandbox-start.py 相同：把自己移进 supervisor 叶子，建 jobs 并启用控制器；
再写测试版执行器编译进去的受信配置，然后一直等到单元被停止。

    holder.py <fixture> <unit> [cpp]
"""
import os
from pathlib import Path
import sys
import time

CONFIG = Path('/var/lib/cherry-sandbox-test/executor.conf')
SERVICE_UID, PAYLOAD_UID, INIT_UID = 61001, 61002, 61006

base = Path(sys.argv[1])
unit = sys.argv[2]
assert base.parent == CONFIG.parent and not base.is_symlink()
assert unit.startswith('cherry-sandbox-test-')
relative = Path('/proc/self/cgroup').read_text().strip().split('::', 1)[1]
group = Path('/sys/fs/cgroup' + relative)
assert group.name == unit + '.service'
(group / 'supervisor').mkdir()
(group / 'supervisor/cgroup.procs').write_text(str(os.getpid()))
(group / 'cgroup.subtree_control').write_text('+cpu +memory +pids')
jobs = group / 'jobs'
jobs.mkdir()
(jobs / 'cgroup.subtree_control').write_text('+cpu +memory +pids')
fixture = base / 'cpp-rootfs-v2' if 'cpp' in sys.argv[3:] else base
boxes = base / 'boxes'
boxes.mkdir(mode=0o700)
os.chown(boxes, SERVICE_UID, SERVICE_UID)
CONFIG.write_text(f'rootfs={fixture / "rootfs"}\nboxes={boxes}\ncgroup={jobs}\nbox_count=2\n'
                  f'service_uid={SERVICE_UID}\nservice_gid={SERVICE_UID}\n'
                  f'payload_uid={PAYLOAD_UID}\npayload_gid={PAYLOAD_UID}\n'
                  f'init_uid={INIT_UID}\ninit_gid={INIT_UID}\n')
CONFIG.chmod(0o644)
(base / 'holder-ready').write_text(str(jobs))
print('holding', jobs, flush=True)
while True:
    time.sleep(60)
