"""测试专用：按生产单元的方式启动 judge（执行层在进程内），只作用于本次测试夹具与单元。

与 deploy/sandbox-linux/systemd/cherry-sandbox-judge.service 一致：服务身份非 root、没有有效能力，
能力边界集只保留 setuid 执行器需要的 8 项，Delegate 委派 cpu/memory/pids，
由 judge-start.py 核对 rootfs、建 supervisor/jobs，再 exec judge。节点链路关闭：测试直接调
/judge 的 trial，不需要控制面与测试数据。

测试机上刻意不建 61001 等账户，systemd 无法按 User= 解析它们；所以单元以 root 启动本文件，
由它做 systemd 本该做的事——把委派子树交给服务身份、降到服务身份——再 exec judge-start.py。
"""
import hashlib
import http.client
import json
import os
from pathlib import Path
import subprocess
import sys
import time

CONFIG = Path('/var/lib/cherry-sandbox-test/executor.conf')
SERVICE_UID, PAYLOAD_UID, INIT_UID = 61001, 61002, 61006
CAPABILITIES = 'CAP_SYS_ADMIN CAP_SETUID CAP_SETGID CAP_SETPCAP CAP_CHOWN CAP_DAC_OVERRIDE CAP_MKNOD CAP_KILL'


def write_root_file(path, text, mode=0o644):
    path.write_text(text)
    os.chown(path, 0, 0)
    path.chmod(mode)


def launch(base, unit, *, port=15051, parallelism=1, cpp=True, seconds=180):
    """在 systemd-run 单元里启动 judge 并等待就绪。返回单元的 cgroup 目录。"""
    fixture = base / 'cpp-rootfs-v2' if cpp else base
    group = Path('/sys/fs/cgroup/system.slice') / (unit + '.service')
    service = base / 'service'
    service.mkdir(mode=0o700, exist_ok=True)
    os.chown(service, SERVICE_UID, SERVICE_UID)
    boxes = service / 'boxes'
    testdata = service / 'testdata'
    testdata.mkdir(mode=0o700, exist_ok=True)
    os.chown(testdata, SERVICE_UID, SERVICE_UID)
    write_root_file(CONFIG, f'rootfs={fixture / "rootfs"}\nboxes={boxes}\ncgroup={group / "jobs"}\n'
                            f'box_count={parallelism}\nservice_uid={SERVICE_UID}\nservice_gid={SERVICE_UID}\n'
                            f'payload_uid={PAYLOAD_UID}\npayload_gid={PAYLOAD_UID}\n'
                            f'init_uid={INIT_UID}\ninit_gid={INIT_UID}\n')
    config = dict(logging=dict(path=str(service / 'logs')), judge=dict(
        httpAddr='127.0.0.1:' + str(port), testdataRoot=str(testdata),
        node=dict(enabled=False),
        compile=dict(cpuNs=10_000_000_000, memoryBytes=256 << 20, clockNs=20_000_000_000)),
        execution=dict(backend='linux', executorPath=str(base / 'sandbox-executor'), boxesRoot=str(boxes),
                       parallelism=parallelism, queueSize=4,
                       store=dict(root=str(service / 'blobs'), maxBlobBytes=64 << 20, maxTotalBytes=256 << 20,
                                  maxEntries=128, retention='1h')))
    # JSON 是 YAML 子集，避免远端安装 YAML 库。
    write_root_file(base / 'judge.yaml', json.dumps(config))
    manifest = fixture / 'manifest.json'
    start = dict(group=str(group), rootfs=str(fixture / 'rootfs'), manifest=str(manifest),
                 manifestSha256=hashlib.sha256(manifest.read_bytes()).hexdigest(),
                 supervisor={'memory.max': str(640 << 20), 'memory.swap.max': '0', 'pids.max': '160',
                             'cpu.max': '100000 100000'},
                 jobs={'memory.max': str(640 << 20), 'memory.swap.max': '0', 'pids.max': '160',
                       'cpu.max': '100000 100000', 'memory.oom.group': '1'},
                 command=[str(base / 'judge'), '-config', str(base / 'judge.yaml')])
    write_root_file(base / 'judge-start.json', json.dumps(start))
    properties = ['Delegate=yes',
                  'CapabilityBoundingSet=' + CAPABILITIES, 'MemoryMax=1280M', 'MemorySwapMax=0', 'TasksMax=320',
                  'CPUQuota=200%', f'RuntimeMaxSec={seconds}', 'KillMode=control-group', 'UMask=0077']
    command = ['systemd-run', '--collect', '--unit=' + unit]
    for p in properties:
        command += ['-p', p]
    subprocess.run(command + ['/usr/bin/python3', str(base / 'judge_service.py'), str(base / 'judge-start.py'),
                              str(base / 'judge-start.json')], check=True)
    wait_ready(port, unit)
    return group


def ready(port):
    connection = http.client.HTTPConnection('127.0.0.1', port, timeout=.5)
    try:
        connection.request('GET', '/version')
        response = connection.getresponse()
        return response.status == 200 and json.loads(response.read())['name'] == 'cherry-oj-judge'
    except (OSError, ValueError, http.client.HTTPException):
        return False
    finally:
        connection.close()


def wait_ready(port, unit, seconds=45):
    # 启动要核对整棵 rootfs 并用一次真实执行冒烟，C++ 工具链 rootfs 需要几秒。
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if ready(port):
            return
        state = subprocess.run(['systemctl', 'show', unit, '-p', 'ActiveState', '--value'],
                               capture_output=True, text=True).stdout.strip()
        if state in ('failed', 'inactive') and time.monotonic() > deadline - seconds + 2:
            break
        time.sleep(.1)
    journal = subprocess.run(['journalctl', '--unit=' + unit, '--no-pager', '-n', '60'],
                             capture_output=True, text=True).stdout
    raise TimeoutError('judge did not become ready\n' + journal)


def enter_service(start, config):
    """在单元内以 root 运行：像 systemd 对 User= 的委派单元那样交出子树，再降权 exec。"""
    relative = Path('/proc/self/cgroup').read_text().strip().split('::', 1)[1]
    group = Path('/sys/fs/cgroup' + relative)
    for path in (group, group / 'cgroup.procs', group / 'cgroup.subtree_control', group / 'cgroup.threads'):
        os.chown(path, SERVICE_UID, SERVICE_UID)
    os.setgroups([])
    os.setgid(SERVICE_UID)
    os.setuid(SERVICE_UID)
    os.execv('/usr/bin/python3', ['/usr/bin/python3', start, config])


if __name__ == '__main__':
    enter_service(sys.argv[1], sys.argv[2])
