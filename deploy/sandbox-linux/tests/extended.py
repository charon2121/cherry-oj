#!/usr/bin/env python3
"""串行扩展测试，失败立即停止，不把 skip 或启动失败算成功。

    extended.py <fixture>
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


def run(mode, **limits):
    start = time.monotonic()
    facts, out, err, _ = executor.call(['probe', mode], **limits)
    facts.update(mode=mode, observedSeconds=round(time.monotonic() - start, 3),
                 stdout=out.decode(errors='replace')[:500], stderr=err.decode(errors='replace')[:500])
    print(json.dumps(facts), flush=True)
    return facts


result = run('background')
assert result['exitCode'] == 0 and not result['reason'] and 'background-started' in result['stdout']
assert result['observedSeconds'] < 2  # 后代自行睡 4s；必须在它自行退出之前完成整组清理。
result = run('processes')
assert result['pidsMaxEvents'] > 0 and 'spawn-denied' in result['stdout']
assert result['observedSeconds'] < 2
result = run('threads')
assert result['pidsMaxEvents'] > 0 and 'threads-denied' in result['stdout']
result = run('sleep', clock_ns=150000000)
assert result['reason'] == 'wall' and result['observedSeconds'] < 1
# 只允许 1 个进程时 init 无法再 fork 出用户程序：这是平台启动失败，不能放大预算硬跑。
result = run('sleep', max_processes=1)
assert result['reason'] == 'platform' and result['pidsMaxEvents'] > 0
# 调用方关闭取消管道必须结束在途睡眠；下一次执行随即成功，不等原程序的 4 秒。
proc, cancel = executor.start(['probe', 'sleep'])
time.sleep(.15)
cancel()
facts, _, _, _ = executor.finish(proc, cancel)
assert facts['reason'] == 'cancelled' and facts['cancelled'], facts
print(json.dumps(dict(mode='cancel', **facts)), flush=True)
result = run('identity')
assert not result['reason'] and result['exitCode'] == 0
print('extended assertions passed', flush=True)
