#!/usr/bin/env python3
"""静态探针串行冒烟：身份、CPU、内存、输出、网络。只用于测试夹具，成功不代表完整验收。

    smoke.py <fixture>
"""
import json
from pathlib import Path
import sys
import time

from executor_client import Executor, drop_to_service

base = Path(sys.argv[1])
assert base.parent == Path('/var/lib/cherry-sandbox-test')
drop_to_service()
executor = Executor(base)
jobs = Path((base / 'holder-ready').read_text())

for mode in ['identity', 'cpu', 'memory', 'output', 'identity', 'network', 'identity']:
    start = time.monotonic()
    facts, stdout, _, _ = executor.call(['probe', mode], memory_bytes=64 << 20, stdout_max_bytes=8192,
                                        stderr_max_bytes=4096)
    assert not facts['error'], facts
    # 执行器返回前已删除执行组：每次执行结束，jobs 下不能有残留。
    assert not [p for p in jobs.iterdir() if p.is_dir()], 'execution group remained'
    if mode == 'identity':
        identity = json.loads(stdout)
        fields = identity['status']
        assert facts['exitCode'] == 0 and facts['reason'] == ''
        assert fields['Uid'].split() == ['61002'] * 4
        assert fields['Gid'].split() == ['61002'] * 4
        assert all(int(v, 16) == 0 for k, v in fields.items() if k.startswith('Cap'))
        assert fields['NoNewPrivs'] == '1' and fields['Seccomp'] == '2'
        assert fields['hostFileAbsent'] == 'true' and fields['rootWriteDenied'] == 'true'
        assert identity['ppid'] == 1
        assert all(target.startswith(('pipe:', '/work/.stdin')) for target in identity['fds'])
        facts['identity'] = identity
    elif mode == 'cpu':
        assert facts['reason'] == 'cpu' and facts['cpuNs'] >= 1000000000
    elif mode == 'memory':
        assert facts['oomKill'] > 0 and facts['oom'] > 0 and facts['signal'] == 9 and facts['reason'] == ''
    elif mode == 'output':
        assert facts['reason'] == 'output' and facts['outputExceeded'] and len(stdout) == 8192
    elif mode == 'network':
        assert facts['signal'] == 31 and facts['reason'] == ''
    facts['mode'] = mode
    facts['observedSeconds'] = round(time.monotonic() - start, 3)
    print(json.dumps(facts), flush=True)
