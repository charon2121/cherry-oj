"""Deadline and output bounds for CI commands; service cleanup belongs to the suite."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import time

LOG_LIMIT = 2 << 20
DIRECTORY_LIMIT = 18 << 20  # Reserve the remaining 2 MiB for report/environment/cleanup JSON.


def run(argv, log, timeout, *, cwd=None, env=None):
    """No shell, no unlimited communicate buffer, and kill the owned process group on failure."""
    log = Path(log)
    used = sum(path.stat().st_size for path in log.parent.iterdir() if path.is_file())
    limit = min(LOG_LIMIT, DIRECTORY_LIMIT - used)
    if limit <= 0:
        raise RuntimeError('suite log budget exhausted')
    with log.open('xb') as output:
        process = subprocess.Popen([str(a) for a in argv], cwd=cwd, env=env,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        size = 0
        deadline = time.monotonic() + timeout
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(process.stdout, selectors.EVENT_READ)
                while selector.get_map():
                    if time.monotonic() >= deadline:
                        raise TimeoutError('command deadline exceeded; see ' + str(log))
                    for key, _ in selector.select(min(.1, max(0, deadline - time.monotonic()))):
                        data = os.read(key.fileobj.fileno(), 65536)
                        if not data:
                            selector.unregister(key.fileobj)
                            continue
                        remaining = limit - size
                        output.write(data[:remaining])
                        size += len(data)
                        if size > limit:
                            raise RuntimeError('command output limit exceeded; see ' + str(log))
            code = process.wait(timeout=max(.01, deadline - time.monotonic()))
            if code:
                raise RuntimeError('command exited ' + str(code) + '; see ' + str(log))
            if size == 0:
                output.write(b'Command completed with exit status 0.\n')
        finally:
            # While the leader is unreaped its PID cannot be reused by an unrelated process group.
            # A descendant holding the pipe open reaches this path through the deadline above.
            if process.returncode is None:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            process.wait(timeout=5)
            process.stdout.close()


def cancel(signum, _frame):
    raise InterruptedError('CI received signal ' + str(signum))


def install_signal_handlers():
    signal.signal(signal.SIGTERM, cancel)
    signal.signal(signal.SIGINT, cancel)
