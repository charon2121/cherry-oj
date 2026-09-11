"""Read execution-group facts alongside live HTTP calls; never infer run wall time from HTTP."""
from contextlib import AbstractContextManager
import json
import os
from pathlib import Path
import threading
import time

JOBS = Path('/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/cherry-sandbox-helper.service/jobs')


def counters(text):
    return {key: int(value) for key, value in (line.split() for line in text.splitlines())}


def read_fd(fd):
    os.lseek(fd, 0, os.SEEK_SET)
    return os.read(fd, 4096).decode()


class Observer(AbstractContextManager):
    def __init__(self, directory, output):
        self.directory, self.output = Path(directory), Path(output)
        self.stop = threading.Event()
        self.failure = None
        self.records = []
        self.max_sample_gap_ns = 0
        self.thread = threading.Thread(target=self.watch, daemon=True)

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, *_):
        self.stop.set()
        self.thread.join(timeout=5)
        if self.thread.is_alive():
            raise RuntimeError('execution observer failed to stop')
        (self.output / 'execution-observations.json').write_text(json.dumps(dict(
            sampleIntervalNs=1000000, maxSampleGapNs=self.max_sample_gap_ns,
            records=self.records, error=self.failure), indent=2) + '\n')
        if self.failure:
            raise RuntimeError('execution observation failed')

    def watch(self):
        active = {}
        previous = time.monotonic_ns()
        try:
            while not self.stop.wait(.001):
                marker = self.directory / 'observed-case'
                case = marker.read_text().strip() if marker.exists() else ''
                if case not in {'cpu', 'memory', ''}:
                    raise ValueError('unknown observed live case')
                now = time.monotonic_ns()
                if case or active:
                    self.max_sample_gap_ns = max(self.max_sample_gap_ns, now - previous)
                previous = now
                for group in JOBS.glob('*'):
                    if group.name in active or not case:
                        continue
                    try:
                        pids = (group / 'cgroup.procs').read_text().split()
                        for pid in pids:
                            proc = Path('/proc') / pid
                            uid = next(line.split()[1:] for line in (proc / 'status').read_text().splitlines() if line.startswith('Uid:'))
                            argv = (proc / 'cmdline').read_bytes().split(b'\0')[0]
                            if uid == ['61002'] * 4 and Path(os.fsdecode(argv)).name == 'Main':
                                files = {}
                                try:
                                    for name in ('cpu.stat', 'memory.events'):
                                        files[name] = os.open(group / name, os.O_RDONLY)
                                except BaseException:
                                    for fd in files.values():
                                        os.close(fd)
                                    raise
                                row = dict(case=case, group=group.name, pid=int(pid), firstSeenNs=now, lastSeenNs=now)
                                active[group.name] = (files, row)
                                self.records.append(row)
                                if len(self.records) > 4:
                                    raise ValueError('unexpected extra CPU/memory executions')
                                break
                    except (FileNotFoundError, ProcessLookupError):
                        continue
                for name, (files, row) in list(active.items()):
                    try:
                        row['cpu'] = counters(read_fd(files['cpu.stat']))
                        row['memoryEvents'] = counters(read_fd(files['memory.events']))
                    except OSError:
                        # A deleted cgroup can reject reads even from an existing descriptor.
                        if (JOBS / name).exists():
                            raise
                    if (Path('/proc') / str(row['pid'])).exists() and (JOBS / name).exists():
                        row['lastSeenNs'] = now
                    else:
                        row['goneNs'] = now
                        for fd in files.values():
                            os.close(fd)
                        del active[name]
        except BaseException as error:
            self.failure = type(error).__name__
        finally:
            for files, _ in active.values():
                for fd in files.values():
                    os.close(fd)


def verify_observations(value, live):
    if value.get('error') is not None:
        raise ValueError('observer recorded an error')
    if not 0 < value.get('maxSampleGapNs', 0) < 100000000:
        raise ValueError('observation sampling gap too large to accept timing association')
    for case in ('cpu', 'memory'):
        rows = [r for r in value['records'] if r['case'] == case]
        if len(rows) != 1 or 'goneNs' not in rows[0]:
            raise ValueError('missing or ambiguous execution observation: ' + case)
        row = rows[0]
        observed_wall = row['goneNs'] - row['firstSeenNs']
        if observed_wall <= 0 or observed_wall >= 5_000_000_000:
            raise ValueError('run lifetime did not stay below the historical 10s regression')
        if live[case]['httpNs'] <= observed_wall:
            raise ValueError('HTTP/run timing association is inconsistent')
        if case == 'cpu':
            cpu = live[case]['cpuNs']
            if not 1_000_000_000 <= cpu < 1_500_000_000 or row['cpu']['usage_usec'] < 900000:
                raise ValueError('cumulative CPU budget/overrun outside acceptance range')
        elif row['memoryEvents'].get('oom_kill', 0) < 1:
            raise ValueError('execution-group OOM was not observed')
