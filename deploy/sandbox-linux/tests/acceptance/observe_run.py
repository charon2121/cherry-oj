"""Read-only, bounded observation of one real business payload; no signals or writes."""
import json
from pathlib import Path
import time

jobs = Path('/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/cherry-sandbox-helper.service/jobs')
deadline = time.monotonic() + 60
seen = {}
print('observer-ready', flush=True)
while time.monotonic() < deadline:
    now = time.monotonic_ns()
    live = set()
    for group in jobs.iterdir():
        if not group.is_dir():
            continue
        try:
            pids = (group / 'cgroup.procs').read_text().split()
            for pid in pids:
                proc = Path('/proc') / pid
                status = (proc / 'status').read_text()
                if 'Uid:\t61002\t61002\t61002\t61002' not in status:
                    continue
                command = (proc / 'cmdline').read_bytes().split(b'\0')[0]
                if command.rsplit(b'/', 1)[-1] != b'Main':
                    continue
                live.add(pid)
                row = seen.setdefault(pid, dict(firstObservedNs=now, group=group.name))
                row['lastObservedNs'] = now
                row['cpuStat'] = (group / 'cpu.stat').read_text().strip()
                row['memoryEvents'] = (group / 'memory.events').read_text().strip()
                row['cpuMax'] = (group / 'cpu.max').read_text().strip()
        except (FileNotFoundError, ProcessLookupError):
            continue
    for pid, row in seen.items():
        if pid not in live:
            row['disappearanceObservedNs'] = now
            row['observedLifetimeNs'] = now - row['firstObservedNs']
            row['samplePeriodNs'] = 5_000_000
            print(json.dumps(row), flush=True)
            raise SystemExit(0)
    time.sleep(.005)
raise SystemExit('No completed Main payload observed within 60 s')
