#!/usr/bin/env python3
"""在真实 Linux 内核上测试 sandbox 执行器。必须以 root 在一次性的 CI 虚拟机上运行：
它会创建系统用户、写 /etc/cherry-sandbox/executor.conf、在 cgroup 根下建子树。

    sudo python3 apps/sandbox/tests/run_tests.py --binary apps/sandbox/build/sandbox --probe build/probe
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

ROOT = Path('/var/lib/cherry-sandbox-test')
ROOTFS = ROOT / 'rootfs'
BOXES = ROOT / 'boxes'
CGROUP = Path('/sys/fs/cgroup/cherry-sandbox-test')
JOBS = CGROUP / 'jobs'
CONFIG = Path('/etc/cherry-sandbox/executor.conf')
BINARY = Path('/usr/local/libexec/cherry-sandbox-test/sandbox')
SERVICE = 'cherry-sbx-test'
PAYLOAD_UID, INIT_UID = 61100, 61200
LIMITS = dict(cpu_ns=2_000_000_000, clock_ns=5_000_000_000, memory_bytes=256 << 20, max_processes=32,
              stdout_max_bytes=1 << 20, stderr_max_bytes=64 << 10)


def setup(binary, probe):
    if subprocess.run(['id', SERVICE], capture_output=True).returncode != 0:
        subprocess.run(['useradd', '--system', '--no-create-home', '--shell', '/usr/sbin/nologin', SERVICE], check=True)
    service = pwd.getpwnam(SERVICE)
    shutil.rmtree(ROOT, ignore_errors=True)
    for d in ['work', 'tmp', 'proc', 'dev', '.oldroot', 'usr/bin', 'bin', 'etc']:
        (ROOTFS / d).mkdir(parents=True, exist_ok=True)
    shutil.copy(probe, ROOTFS / 'usr/bin/probe')
    os.chmod(ROOTFS / 'usr/bin/probe', 0o755)
    ROOT.chmod(0o755)
    BOXES.mkdir(mode=0o700)
    os.chown(BOXES, service.pw_uid, service.pw_gid)
    for box in range(2):
        for sub in ['', 'in', 'out']:
            d = BOXES / str(box) / sub
            d.mkdir(mode=0o700, exist_ok=True)
            os.chown(d, service.pw_uid, service.pw_gid)
    # cgroup：根 → cherry-sandbox-test（无进程）→ jobs（启用 cpu/memory/pids）→ box-N（执行器创建）
    for path in [Path('/sys/fs/cgroup'), CGROUP]:
        if path != Path('/sys/fs/cgroup'):
            path.mkdir(exist_ok=True)
        (path / 'cgroup.subtree_control').write_text('+cpu +memory +pids')
    JOBS.mkdir(exist_ok=True)
    (JOBS / 'cgroup.subtree_control').write_text('+cpu +memory +pids')
    CONFIG.parent.mkdir(parents=True, exist_ok=True)
    CONFIG.write_text(f'rootfs={ROOTFS}\nboxes={BOXES}\ncgroup={JOBS}\nbox_count=2\n'
                      f'service_uid={service.pw_uid}\nservice_gid={service.pw_gid}\n'
                      f'payload_uid={PAYLOAD_UID}\npayload_gid={PAYLOAD_UID}\n'
                      f'init_uid={INIT_UID}\ninit_gid={INIT_UID}\n')
    CONFIG.chmod(0o644)
    BINARY.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(binary, BINARY)
    os.chown(BINARY, 0, service.pw_gid)
    BINARY.chmod(0o4750)
    return service


class Box:
    """以 root 准备 box 内容（文件归服务身份），再以服务身份调用执行器。"""

    def __init__(self, service, index=0):
        self.service, self.index, self.dir = service, index, BOXES / str(index)

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
        proc = subprocess.Popen([str(BINARY), '--box', str(self.index)], stdin=read,
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
    return sorted(p.name for p in JOBS.iterdir() if p.is_dir())


class ExecutorTest(unittest.TestCase):
    service = None

    def setUp(self):
        self.box = Box(self.service)

    def tearDown(self):
        # 每次执行结束，执行组都必须已删除。
        self.assertEqual(group_names(), [])

    def test_echo_streams_and_exit(self):
        f = self.box.facts(['probe', 'echo'], stdin=b'1 2\n')
        self.assertEqual((f['exitCode'], f['signal'], f['reason'], f['error']), (0, 0, '', ''))
        self.assertEqual(self.box.read('stdout'), b'1 2\n')
        self.assertEqual(self.box.read('stderr'), b'probe-stderr\n')
        self.assertGreater(f['memoryBytes'], 0)

    def test_exit_code_and_signal(self):
        self.assertEqual(self.box.facts(['probe', 'exit', '7'])['exitCode'], 7)
        f = self.box.facts(['probe', 'segv'])
        self.assertEqual((f['signal'], f['reason']), (11, ''))

    def test_identity_and_filesystem(self):
        f = self.box.facts(['probe', 'identity'], inputs=[('data/in.txt', b'x', False)])
        i = json.loads(self.box.read('stdout'))
        self.assertEqual((i['uid'], i['gid']), (PAYLOAD_UID, PAYLOAD_UID))
        self.assertEqual(i['ppid'], 1)
        for cap in ['CapEff', 'CapPrm', 'CapBnd', 'CapAmb']:
            self.assertEqual(int(i[cap], 16), 0, cap)
        self.assertEqual((i['NoNewPrivs'], i['Seccomp']), ('1', '2'))
        self.assertEqual(i['cwd'], '/work')
        self.assertFalse(i['rootWritable'])
        self.assertFalse(i['hostRootVisible'])
        self.assertTrue(i['input'])
        self.assertTrue(i['tmpWritable'])
        self.assertEqual(i['extraFds'], 0)
        self.assertEqual(f['error'], '')

    def test_command_resolves_from_workspace(self):
        probe = (ROOTFS / 'usr/bin/probe').read_bytes()
        f = self.box.facts(['tool', 'exit', '3'], inputs=[('tool', probe, True)])
        self.assertEqual(f['exitCode'], 3)

    def test_cpu_limit(self):
        f = self.box.facts(['probe', 'spin'], cpu_ns=500_000_000)
        self.assertEqual(f['reason'], 'cpu')
        self.assertGreaterEqual(f['cpuNs'], 500_000_000)
        self.assertLess(f['clockNs'], 2_000_000_000)

    def test_wall_limit(self):
        f = self.box.facts(['probe', 'sleep'], clock_ns=500_000_000)
        self.assertEqual(f['reason'], 'wall')
        self.assertGreaterEqual(f['clockNs'], 500_000_000)
        self.assertLess(f['cpuNs'], 100_000_000)

    def test_memory_limit_is_task_oom(self):
        f = self.box.facts(['probe', 'hog'], memory_bytes=64 << 20)
        self.assertEqual((f['reason'], f['signal']), ('', 9))
        self.assertGreater(f['oom'], 0)
        self.assertGreater(f['oomKill'], 0)

    def test_output_limit(self):
        f = self.box.facts(['probe', 'flood'], stdout_max_bytes=4096)
        self.assertEqual(f['reason'], 'output')
        self.assertTrue(f['outputExceeded'])
        self.assertEqual(len(self.box.read('stdout')), 4096)

    def test_network_is_killed_by_seccomp(self):
        self.assertEqual(self.box.facts(['probe', 'socket'])['signal'], 31)

    def test_x32_abi_is_killed(self):
        self.assertEqual(self.box.facts(['probe', 'x32'])['signal'], 31)

    def test_process_limit(self):
        f = self.box.facts(['probe', 'forks'], max_processes=8)
        self.assertGreater(f['pidsMaxEvents'], 0)
        self.assertLess(int(self.box.read('stdout')), 8)

    def test_background_processes_are_reclaimed(self):
        started = time.monotonic()
        f = self.box.facts(['probe', 'background'])
        self.assertEqual((f['exitCode'], f['reason']), (0, ''))
        self.assertLess(time.monotonic() - started, 5)

    def test_artifacts_are_bounded_regular_files(self):
        f = self.box.facts(['probe', 'write'], outputs=['out.txt', 'sub/deep.txt', 'missing'])
        self.assertEqual(f['error'], '')
        self.assertEqual([(o['path'], o['sizeBytes']) for o in f['outputs']], [('out.txt', 9), ('sub/deep.txt', 5)])
        self.assertEqual(self.box.read('artifact-0'), b'artifact\n')
        self.assertEqual(self.box.read('artifact-1'), b'deep\n')
        # 目录不是可交付的产物；只要有一个声明的产物不合格，整次交付都不成立。
        f = self.box.facts(['probe', 'write'], outputs=['out.txt', 'sub'])
        self.assertEqual((f['reason'], f['outputs']), ('platform', []))

    def test_links_cannot_be_created(self):
        for mode in ['symlink', 'hardlink']:
            self.assertEqual(self.box.facts(['probe', mode])['signal'], 31, mode)

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
        stale = JOBS / 'box-1'
        stale.mkdir()
        sleeper = subprocess.Popen(['sleep', '100'])
        (stale / 'cgroup.procs').write_text(str(sleeper.pid))
        f = Box(self.service, 1).facts(['probe', 'exit', '0'])
        self.assertEqual(f['exitCode'], 0)
        self.assertIsNotNone(sleeper.wait(timeout=10))

    def test_parallel_boxes(self):
        results = {}

        def run(index):
            results[index] = Box(self.service, index).facts(['probe', 'spin'], cpu_ns=300_000_000)

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
    args, rest = parser.parse_known_args()
    if os.geteuid() != 0:
        sys.exit('must run as root on a disposable machine')
    ExecutorTest.service = setup(args.binary, args.probe)
    unittest.main(argv=[sys.argv[0], '-v', *rest])


if __name__ == '__main__':
    main()
