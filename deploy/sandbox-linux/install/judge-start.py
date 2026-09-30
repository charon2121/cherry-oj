#!/usr/bin/env python3
"""sandbox HTTP 服务的启动脚本：核对 rootfs、建好委派子树，再 exec 服务本体。

以服务身份在自己的 systemd 单元里运行（Delegate=cpu memory pids）：

1. rootfs 必须与固定摘要的清单逐文件一致——多一个、少一个、内容或权限不同都拒绝启动。
   执行器每次把这棵树只读挂进隔离环境，被篡改的 rootfs 等于被篡改的编译器与运行时。
2. 把本进程移进 supervisor 叶子，让委派根不含进程，再建 jobs 并启用控制器；
   执行器在 jobs 下为每次执行建组。两支各自有资源上界。
3. exec 服务本体（参数取自配置）。

配置只接受 root 管理的文件：文件与每一级祖先都属于 root 且不可被组或其他人写入。
"""
import hashlib
import json
import os
from pathlib import Path
import stat
import sys

MOUNT_POINTS = ('work', 'tmp', 'proc', 'dev', '.oldroot')


def protected(path):
    for item in [path, *path.parents]:
        info = item.lstat()
        if info.st_uid != 0 or info.st_mode & 0o022 or stat.S_ISLNK(info.st_mode):
            raise ValueError('not protected by root: ' + str(item))


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        while chunk := stream.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def read_manifest(path, expected):
    protected(path)
    data = path.read_bytes()
    if len(data) > 8 << 20 or hashlib.sha256(data).hexdigest() != expected:
        raise ValueError('rootfs manifest digest mismatch')
    manifest = json.loads(data)
    if manifest.get('Version') != 1 or not manifest.get('Source') or not manifest.get('Entries'):
        raise ValueError('rootfs manifest is missing version, source or entries')
    entries = {}
    for entry in manifest['Entries']:
        rel = entry['Path']
        parts = rel.split('/')
        if rel in entries or rel.startswith('/') or any(p in ('', '.', '..') for p in parts):
            raise ValueError('invalid manifest path: ' + rel)
        entries[rel] = entry
    return entries


def check_entry(path, rel, entry):
    info = path.lstat()
    kind = stat.S_IFMT(info.st_mode)
    if info.st_uid != 0 or (info.st_mode & 0o022 and kind != stat.S_IFLNK):
        raise ValueError('wrong rootfs ownership or permissions: ' + rel)
    if stat.S_IMODE(info.st_mode) != entry['Mode']:
        raise ValueError('rootfs mode mismatch: ' + rel)
    if kind == stat.S_IFDIR:
        if entry['SHA256'] or entry['Link']:
            raise ValueError('invalid directory manifest entry: ' + rel)
    elif kind == stat.S_IFLNK:
        if os.readlink(path) != entry['Link']:
            raise ValueError('rootfs link mismatch: ' + rel)
    elif kind == stat.S_IFREG:
        if info.st_nlink != 1:
            raise ValueError('rootfs refuses hard links: ' + rel)
        if digest(path) != entry['SHA256']:
            raise ValueError('wrong rootfs file digest: ' + rel)
    else:
        raise ValueError('rootfs refuses special files: ' + rel)


def verify_rootfs(root, entries):
    """同时拒绝多出的与缺少的条目：只检查清单列出的文件，会漏掉 rootfs 中新增的内容。"""
    protected(root)
    remaining = dict(entries)
    for parent, dirs, files in os.walk(root, followlinks=False):
        for name in dirs + files:
            path = Path(parent) / name
            rel = path.relative_to(root).as_posix()
            entry = remaining.pop(rel, None)
            if entry is None:
                raise ValueError('rootfs contains unlisted file: ' + rel)
            check_entry(path, rel, entry)
    if remaining:
        raise ValueError('rootfs is missing manifest entries: ' + ', '.join(sorted(remaining)[:5]))
    for name in MOUNT_POINTS:
        if not stat.S_ISDIR((root / name).lstat().st_mode):
            raise ValueError('mount point is not a directory: ' + name)


def prepare_groups(group, supervisor, jobs):
    actual = Path('/proc/self/cgroup').read_text().strip()
    if actual != '0::' + str(group).removeprefix('/sys/fs/cgroup'):
        raise RuntimeError('unexpected cgroup owner: ' + actual)
    # 服务以 UMask=0077 运行，新建的组目录会是 0700；judge 启动自检要读这两支的资源上界，
    # 所以显式放开读与遍历（控制文件本身由内核按 0644 创建，写仍只属于服务身份）。
    for leaf in ('supervisor', 'jobs'):
        (group / leaf).mkdir(exist_ok=True)
        (group / leaf).chmod(0o755)
    # 启动阶段是单线程的；先把自己移进叶子，委派根才能启用子树控制器。
    (group / 'supervisor/cgroup.procs').write_text(str(os.getpid()))
    (group / 'cgroup.subtree_control').write_text('+cpu +memory +pids')
    (group / 'jobs/cgroup.subtree_control').write_text('+cpu +memory +pids')
    for leaf, limits in (('supervisor', supervisor), ('jobs', jobs)):
        for name, value in limits.items():
            path = group / leaf / name
            path.write_text(value)
            if path.read_text().strip() != value:
                raise RuntimeError(f'{leaf}/{name} read back differently than it was written')


def main():
    config_path = Path(sys.argv[1])
    protected(config_path)
    config = json.loads(config_path.read_text())
    verify_rootfs(Path(config['rootfs']), read_manifest(Path(config['manifest']), config['manifestSha256']))
    prepare_groups(Path(config['group']), config['supervisor'], config['jobs'])
    command = config['command']
    os.execv(command[0], command)


if __name__ == '__main__':
    main()
