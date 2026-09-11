"""Bounded, read-only installation diagnostics outside the observed service cgroup."""
from collections import deque
from contextlib import contextmanager
import json
from pathlib import Path
import threading
import time

STAT_FIELDS = {'anon', 'file', 'kernel', 'kernel_stack', 'pagetables', 'sock', 'shmem',
               'file_mapped', 'file_dirty', 'file_writeback', 'slab', 'slab_reclaimable',
               'slab_unreclaimable'}
EVENT_FIELDS = {'low', 'high', 'max', 'oom', 'oom_kill', 'oom_group_kill'}


def counters(path, fields):
    with path.open() as stream:
        text = stream.read(8193)
    if len(text) > 8192:
        raise ValueError('oversized cgroup counter file')
    result = {}
    for row in text.splitlines():
        key, value = row.split()
        if key in fields:
            result[key] = int(value)
    return result


def snapshot(group):
    # These files are not read atomically. Only memory.peak is a kernel high-water
    # mark; sampled current/stat values cannot reconstruct the composition at it.
    result = {}
    for name in ('memory.current', 'memory.peak', 'memory.swap.current', 'pids.current'):
        with (group / name).open() as stream:
            result[name] = int(stream.read(32))
    result['stat'] = counters(group / 'memory.stat', STAT_FIELDS)
    result['events'] = counters(group / 'memory.events', EVENT_FIELDS)
    result['localEvents'] = counters(group / 'memory.events.local', EVENT_FIELDS)
    return result


class Samples:
    def __init__(self, group):
        self.group = Path(group)
        self.started = time.monotonic_ns()
        self.tail = deque(maxlen=64)
        self.count = self.missing = 0
        self.highest = None
        self.errors = set()

    def sample(self):
        try:
            row = snapshot(self.group)
        except FileNotFoundError:
            self.missing += 1  # Not created yet, or systemd has already collected it.
            return
        except (OSError, ValueError) as error:
            self.errors.add(type(error).__name__)
            return
        row['elapsedNs'] = time.monotonic_ns() - self.started
        self.count += 1
        self.tail.append(row)
        if self.highest is None or row['memory.current'] > self.highest['memory.current']:
            self.highest = row

    def report(self):
        return dict(intervalMs=10, samples=self.count, missing=self.missing,
                    errors=sorted(self.errors), highestCurrent=self.highest, tail=list(self.tail))


@contextmanager
def observe(group, output):
    """The caller supplies its registered service path; this never starts or changes it."""
    samples = Samples(group)
    stop = threading.Event()
    def collect():
        while not stop.is_set():
            samples.sample()
            stop.wait(.01)
    worker = threading.Thread(target=collect, name='install-memory', daemon=True)
    worker.start()
    try:
        yield
    finally:
        stop.set()
        worker.join(timeout=1)
        if worker.is_alive():
            # Do not race report serialization with a still-running observer.
            data = dict(error='observer did not stop')
        else:
            data = samples.report()
        Path(output).write_text(json.dumps(data, indent=2) + '\n')
