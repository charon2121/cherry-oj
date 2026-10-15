#!/usr/bin/env python3
"""在真实 Linux 内核上测试 sandbox 执行器。必须以 root 在一次性的机器上运行。

默认自备全部环境：创建服务用户、写编译进二进制的配置路径、在 cgroup 根下建子树。
在已委派的 systemd 单元里运行时，用 --jobs 指定现成的
jobs 子树、用 --service-uid 指定服务身份、用 --config 指定构建时编译进去的配置路径。

    sudo python3 apps/sandbox/tests/run_tests.py --binary build/sandbox --probe build/probe
"""
import argparse
import json
import os
import pwd
import shutil
import subprocess
import sys
import threading
import time
import unittest
from pathlib import Path
from types import SimpleNamespace

LIMITS = dict(cpu_ns=2_000_000_000, clock_ns=5_000_000_000, memory_bytes=256 << 20, max_processes=32,
              stdout_max_bytes=1 << 20, stderr_max_bytes=64 << 10)
SERVICE_NAME = 'cherry-sbx-test'
env = SimpleNamespace()


def service_identity(uid):
    if uid is not None:
        return SimpleNamespace(pw_uid=uid, pw_gid=uid)
    if subprocess.run(['id', SERVICE_NAME], capture_output=True).returncode != 0:
        subprocess.run(['useradd', '--system', '--no-create-home', '--shell', '/usr/sbin/nologin', SERVICE_NAME],
                       check=True)
    return pwd.getpwnam(SERVICE_NAME)


def prepare_jobs(jobs):
    """没有现成委派时，在 cgroup 根下建：cherry-sandbox-test（无进程）→ jobs（启用控制器）。"""
    if jobs is not None:
        return Path(jobs)
    group = Path('/sys/fs/cgroup/cherry-sandbox-test')
    for path in [Path('/sys/fs/cgroup'), group]:
        path.mkdir(exist_ok=True)
        (path / 'cgroup.subtree_control').write_text('+cpu +memory +pids')
    jobs = group / 'jobs'
    jobs.mkdir(exist_ok=True)
    (jobs / 'cgroup.subtree_control').write_text('+cpu +memory +pids')
    return jobs


def setup(args):
    service = service_identity(args.service_uid)
    root = Path(args.root)
    env.service, env.jobs = service, prepare_jobs(args.jobs)
    env.rootfs, env.boxes = root / 'rootfs', root / 'boxes'
    env.payload_uid, env.init_uid = args.payload_uid, args.init_uid
    env.binary = Path(args.install)
    for path in (env.rootfs, env.boxes):
        shutil.rmtree(path, ignore_errors=True)
    for d in ['work', 'tmp', 'proc', 'dev', '.oldroot', 'usr/bin', 'bin', 'etc']:
        (env.rootfs / d).mkdir(parents=True, exist_ok=True)
    for name in ('probe', 'true'):
        shutil.copy(args.probe, env.rootfs / 'usr/bin' / name)
        os.chmod(env.rootfs / 'usr/bin' / name, 0o755)
    root.chmod(0o755)
    env.boxes.mkdir(mode=0o700)
    os.chown(env.boxes, service.pw_uid, service.pw_gid)
    for box in range(2):
        for sub in ['', 'in', 'out']:
            d = env.boxes / str(box) / sub
            d.mkdir(mode=0o700, exist_ok=True)
            os.chown(d, service.pw_uid, service.pw_gid)
    config = Path(args.config)
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text(f'rootfs={env.rootfs}\nboxes={env.boxes}\ncgroup={env.jobs}\nbox_count=2\n'
                      f'service_uid={service.pw_uid}\nservice_gid={service.pw_gid}\n'
                      f'payload_uid={env.payload_uid}\npayload_gid={env.payload_uid}\n'
                      f'init_uid={env.init_uid}\ninit_gid={env.init_uid}\n')
    config.chmod(0o644)
    env.binary.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(args.binary, env.binary)
    os.chown(env.binary, 0, service.pw_gid)
    env.binary.chmod(0o4750)


class Box:
    """以 root 准备 box 内容（文件归服务身份），再以服务身份调用执行器。"""

    def __init__(self, service, index=0):
        self.service, self.index, self.dir = service, index, env.boxes / str(index)

    def prepare(self, args, inputs=(), stdin=b'', outputs=(), env=(), **limits):
        for sub in ['in', 'out']:
            for f in (self.dir / sub).iterdir():
                shutil.rmtree(f) if f.is_dir() else f.unlink()
        records = [f'arg={a}' for a in args] + [f'env={e}' for e in env]
        for i, (path, data, executable) in enumerate(inputs):
            records.append(f'input={int(executable)}:{path}')
            self.write(f'in/{i}', data)
        records += [f'output={o}' for o in outputs]
        records += [f'{k}={v}' for k, v in {**LIMITS, **limits}.items()]
        self.write('spec', b''.join(r.encode() + b'\0' for r in records))
        self.write('stdin', stdin)

    def write(self, name, data):
        p = self.dir / name
        p.write_bytes(data)
        os.chown(p, self.service.pw_uid, self.service.pw_gid)
        p.chmod(0o600)

    def start(self):
        """stdin 接一根取消管道：执行期间必须保持写端打开，关闭它就是取消。"""
        read, write = os.pipe()
        proc = subprocess.Popen([str(env.binary), '--box', str(self.index)], stdin=read,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                user=self.service.pw_uid, group=self.service.pw_gid, extra_groups=[])
        os.close(read)
        proc.cancel = lambda: os.close(write)
        proc.finish = lambda: self.finish(proc, write)
        return proc

    @staticmethod
    def finish(proc, write):
        out, err = proc.communicate(timeout=60)
        try:
            os.close(write)
        except OSError:
            pass  # 已经取消过
        return proc.returncode, out.decode(), err.decode()

    def run(self, *args, cancel_after=None, **kwargs):
        self.prepare(*args, **kwargs)
        proc = self.start()
        if cancel_after is not None:
            threading.Timer(cancel_after, proc.cancel).start()
        return proc.finish()

    def facts(self, *args, **kwargs):
        code, out, err = self.run(*args, **kwargs)
        if code != 0:
            raise AssertionError(f'exit {code}: {err}')
        return json.loads(out)

    def read(self, name):
        return (self.dir / 'out' / name).read_bytes()


def group_names():
    return sorted(p.name for p in env.jobs.iterdir() if p.is_dir())


class ExecutorTest(unittest.TestCase):
    def setUp(self):
        self.service = env.service
        self.box = Box(self.service)

    def tearDown(self):
        # 每次执行结束，执行组都必须已删除。
        self.assertEqual(group_names(), [])

    def test_command_resolves_from_workspace(self):
        probe = (env.rootfs / 'usr/bin/probe').read_bytes()
        f = self.box.facts(['tool', 'exit', '3'], inputs=[('tool', probe, True)])
        self.assertEqual(f['exitCode'], 3)

    def test_artifacts_are_bounded_regular_files(self):
        f = self.box.facts(['probe', 'write'], outputs=['out.txt', 'sub/deep.txt', 'missing'])
        self.assertEqual(f['error'], '')
        self.assertEqual([(o['path'], o['sizeBytes']) for o in f['outputs']], [('out.txt', 9), ('sub/deep.txt', 5)])
        self.assertEqual(self.box.read('artifact-0'), b'artifact\n')
        self.assertEqual(self.box.read('artifact-1'), b'deep\n')
        # 目录不是可交付的产物；只要有一个声明的产物不合格，整次交付都不成立。
        f = self.box.facts(['probe', 'write'], outputs=['out.txt', 'sub'])
        self.assertEqual((f['reason'], f['outputs']), ('platform', []))

    def test_missing_command_is_platform_failure(self):
        f = self.box.facts(['no-such-command'])
        self.assertEqual(f['reason'], 'platform')
        self.assertIn('init failed', f['error'])

    def test_cancel_by_closing_stdin(self):
        code, out, err = self.box.run(['probe', 'sleep'], cancel_after=0.5)
        f = json.loads(out)
        self.assertEqual((code, f['reason'], f['cancelled']), (0, 'cancelled', True))

    def test_invalid_requests_are_refused(self):
        for args, kwargs in [(['/usr/bin/probe'], {}), (['probe'], {'inputs': [('../x', b'', False)]}),
                             (['probe'], {'cpu_ns': 0})]:
            code, _, err = self.box.run(args, **kwargs)
            self.assertEqual(code, 1, err)

    def test_busy_box_is_refused(self):
        self.box.prepare(['probe', 'sleep'], clock_ns=2_000_000_000)
        first = self.box.start()
        time.sleep(0.3)
        second = self.box.start()
        code, _, err = second.finish()
        self.assertEqual(code, 1)
        self.assertIn('busy', err)
        first.finish()

    def test_stale_group_is_reclaimed(self):
        stale = env.jobs / 'box-1'
        stale.mkdir()
        sleeper = subprocess.Popen(['sleep', '100'])
        (stale / 'cgroup.procs').write_text(str(sleeper.pid))
        f = Box(self.service, 1).facts(['probe', 'exit', '0'])
        self.assertEqual(f['exitCode'], 0)
        self.assertIsNotNone(sleeper.wait(timeout=10))

    def test_parallel_boxes(self):
        results = {}

        def run(index):
            results[index] = Box(self.service, index).facts(['probe', 'cpu'], cpu_ns=300_000_000)

        threads = [threading.Thread(target=run, args=(i,)) for i in range(2)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        self.assertEqual([results[i]['reason'] for i in range(2)], ['cpu', 'cpu'])

    def test_repeated_runs_do_not_leak(self):
        for _ in range(200):
            self.assertEqual(self.box.facts(['probe', 'exit', '0'])['exitCode'], 0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--binary', required=True)
    parser.add_argument('--probe', required=True)
    parser.add_argument('--root', default='/var/lib/cherry-sandbox-test')
    parser.add_argument('--config', default='/opt/cherry-oj/etc/executor.conf',
                        help='必须与构建时编译进二进制的路径一致')
    parser.add_argument('--install', default='/usr/local/libexec/cherry-sandbox-test/sandbox')
    parser.add_argument('--jobs', help='已委派且启用了 cpu/memory/pids 的 jobs 子树')
    parser.add_argument('--service-uid', type=int, help='服务身份（uid 与 gid 相同）；缺省时创建系统用户')
    parser.add_argument('--payload-uid', type=int, default=61100)
    parser.add_argument('--init-uid', type=int, default=61200)
    args, rest = parser.parse_known_args()
    if os.geteuid() != 0:
        sys.exit('must run as root on a disposable machine')
    setup(args)
    unittest.main(argv=[sys.argv[0], '-v', *rest])


if __name__ == '__main__':
    main()
