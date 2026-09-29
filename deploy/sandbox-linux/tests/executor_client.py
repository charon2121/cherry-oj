"""测试专用：以服务身份准备 box 并直接调用 setuid 执行器（apps/sandbox），不经过 HTTP。

box 约定见 apps/sandbox/README.md。调用方可以是 root（写文件后交还服务身份），
也可以已经降到服务身份；执行器本身始终以服务身份启动。
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import threading

SERVICE_UID = 61001
LIMITS = dict(cpu_ns=1_000_000_000, clock_ns=5_000_000_000, memory_bytes=128 << 20, max_processes=64,
              stdout_max_bytes=16384, stderr_max_bytes=16384)


class Executor:
    def __init__(self, base, box=0):
        self.binary = Path(base) / 'sandbox-executor'
        self.dir = Path(base) / 'boxes' / str(box)
        self.box = box

    def own(self, path):
        if os.geteuid() == 0:
            os.chown(path, SERVICE_UID, SERVICE_UID)

    def write(self, name, data):
        path = self.dir / name
        path.write_bytes(data)
        path.chmod(0o600)
        self.own(path)

    def prepare(self, command, inputs, outputs, stdin, env, limits):
        shutil.rmtree(self.dir, ignore_errors=True)
        for d in (self.dir, self.dir / 'in', self.dir / 'out'):
            d.mkdir(mode=0o700)
            self.own(d)
        records = [f'arg={a}' for a in command] + [f'env={e}' for e in env]
        for i, (path, data, executable) in enumerate(inputs):
            records.append(f'input={int(executable)}:{path}')
            self.write(f'in/{i}', data)
        records += [f'output={o}' for o in outputs]
        records += [f'{k}={v}' for k, v in {**LIMITS, **limits}.items()]
        self.write('spec', b''.join(r.encode() + b'\0' for r in records))
        self.write('stdin', stdin)

    def start(self, command, inputs=(), outputs=(), stdin=b'', env=(), **limits):
        """启动一次执行，返回 (进程, 取消函数)。stdin 上的取消管道在执行期间保持打开。"""
        self.prepare(command, inputs, outputs, stdin, env, limits)
        read, write = os.pipe()
        drop = dict(user=SERVICE_UID, group=SERVICE_UID, extra_groups=[]) if os.geteuid() == 0 else {}
        proc = subprocess.Popen([str(self.binary), '--box', str(self.box)], stdin=read, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, **drop)
        os.close(read)
        closed = threading.Lock()

        def cancel():
            if closed.acquire(blocking=False):
                os.close(write)
        return proc, cancel

    def finish(self, proc, cancel, timeout=60):
        """等执行结束，返回 (事实, stdout, stderr, 产物)。退出码非 0 时抛出，由调用方断言。"""
        out, err = proc.communicate(timeout=timeout)
        cancel()
        if proc.returncode != 0:
            raise ExecutorError(proc.returncode, err.decode(errors='replace'))
        facts = json.loads(out)
        stdout = (self.dir / 'out/stdout').read_bytes()
        stderr = (self.dir / 'out/stderr').read_bytes()
        artifacts = {o['path']: (self.dir / 'out' / ('artifact-' + str(o['index']))).read_bytes()
                     for o in facts['outputs']}
        assert len(stdout) == facts['stdoutBytes'] and len(stderr) == facts['stderrBytes'], facts
        return facts, stdout, stderr, artifacts

    def call(self, command, **kwargs):
        return self.finish(*self.start(command, **kwargs))


class ExecutorError(Exception):
    def __init__(self, code, stderr):
        super().__init__(f'executor exited {code}: {stderr.strip()}')
        self.code, self.stderr = code, stderr


def drop_to_service():
    os.setgroups([])
    os.setgid(SERVICE_UID)
    os.setuid(SERVICE_UID)
