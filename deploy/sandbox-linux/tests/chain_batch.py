#!/usr/bin/env python3
"""仅管理 work048-chain 专用测试单元。单批 180s 上限，finally 停止本批服务。

    chain_batch.py <fixture> <smoke|repeat|concurrency> <unit>
"""
from pathlib import Path
import re
import subprocess
import sys

import http_service

base = Path(sys.argv[1])
mode = sys.argv[2]
unit = sys.argv[3]
assert base.parent == Path('/var/lib/cherry-sandbox-test') and base.name.startswith('work048-chain-')
assert mode in ('smoke', 'repeat', 'concurrency')
assert re.fullmatch('cherry-sandbox-test-work048-chain-' + mode + '-[a-z0-9-]+', unit)
server, driver = unit + '-http', unit + '-driver'
assert not subprocess.check_output(['systemctl', 'list-units', '--all', '--no-legend', unit + '-*'], text=True).strip()
try:
    http_service.launch(base, server, port=15050, parallelism=2 if mode == 'concurrency' else 1)
    subprocess.run(['systemctl', 'show', server, '-p', 'MemoryMax', '-p', 'TasksMax', '-p', 'CPUQuotaPerSecUSec',
                    '-p', 'MainPID'], check=True)
    subprocess.run(['systemd-run', '--unit=' + driver, '--collect', '--wait', '--pipe', '-p', 'MemoryMax=128M',
                    '-p', 'MemorySwapMax=0', '-p', 'TasksMax=16', '-p', 'CPUQuota=50%', '-p', 'RuntimeMaxSec=150',
                    'python3', str(base / 'http_chain.py'), str(base), mode, unit], check=True, text=True)
except BaseException:
    # 先保留服务状态再停止：重置不等于证明服务崩溃过。
    subprocess.run(['systemctl', 'show', server, '-p', 'ActiveState', '-p', 'MainPID', '-p', 'Result',
                    '-p', 'ExecMainStatus'], check=False, timeout=5)
    subprocess.run(['journalctl', '--unit=' + server, '--no-pager', '-n', '80'], check=False, timeout=5)
    raise
finally:
    subprocess.run(['systemctl', 'stop', server], check=False)
    path = Path('/sys/fs/cgroup/system.slice') / (server + '.service')
    assert not path.exists(), ('remaining group', path)
