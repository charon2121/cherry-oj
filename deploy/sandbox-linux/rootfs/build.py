#!/usr/bin/env python3
"""离线组装 rootfs；只解包已锁定的 deb，不运行包维护脚本，不安装宿主软件。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess


def digest(path):
    with open(path, 'rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def seal(root, source):
    """清单包含所有目录、文件和链接；运行时额外要求整个树属于 root。"""
    for name in ('work', 'tmp', 'proc', 'dev', '.oldroot', '.sandbox'):
        path = root / name
        if path.is_symlink() or (path.exists() and not path.is_dir()):
            raise ValueError(f'不安全的挂载点: {name}')
        path.mkdir(exist_ok=True)
    launcher = root / '.sandbox/launcher'
    if launcher.exists() or launcher.is_symlink():
        raise ValueError('输入包不能提供自研 launcher')
    launcher.touch(mode=0o500)
    entries = []
    for parent, dirs, files in os.walk(root, followlinks=False):
        for name in sorted(dirs + files):
            path = Path(parent) / name
            st = path.lstat()
            relative = path.relative_to(root).as_posix()
            entry = {'Path': relative, 'SHA256': '', 'Link': '', 'Mode': 0}
            if stat.S_ISLNK(st.st_mode):
                entry['Link'] = os.readlink(path)
            elif stat.S_ISREG(st.st_mode):
                if st.st_nlink != 1:
                    # deb 中的硬链接转为独立 inode，不把链接关系带入运行时。
                    tmp = path.with_name(path.name + '.cherry-copy')
                    if tmp.exists() or tmp.is_symlink():
                        raise ValueError('硬链接转换临时名冲突')
                    shutil.copyfile(path, tmp)
                    os.chmod(tmp, stat.S_IMODE(st.st_mode))
                    os.replace(tmp, path)
                os.chmod(path, stat.S_IMODE(st.st_mode) & 0o755)
                entry['SHA256'] = digest(path)
            elif stat.S_ISDIR(st.st_mode):
                os.chmod(path, 0o700 if relative == '.sandbox' else 0o755)
            else:
                raise ValueError(f'rootfs 不允许特殊文件: {relative}')
            entry['Mode'] = stat.S_IMODE(path.lstat().st_mode)
            entries.append(entry)
    os.chmod(root, 0o755)
    entries.sort(key=lambda entry: entry['Path'])
    return {'Version': 1, 'Source': source, 'Entries': entries}


def apply_layout(root, layout):
    """Ubuntu/Debian usr-merged 布局必须在锁文件显式声明，不能借宿主加载器。"""
    if layout is None:
        return
    if layout != 'usr-merged':
        raise ValueError('不支持的 rootfs 布局')
    for name in ('bin', 'sbin', 'lib', 'lib64'):
        target = root / 'usr' / name
        if not target.is_dir():
            continue
        alias = root / name
        if alias.is_symlink():
            if os.readlink(alias) != 'usr/' + name:
                raise ValueError('根目录布局链接不匹配: ' + name)
        elif alias.exists():
            raise ValueError('根目录布局条目冲突: ' + name)
        else:
            alias.symlink_to('usr/' + name)


def build(lock_path, packages, output):
    lock = json.loads(lock_path.read_text())
    if lock.get('version') != 1 or lock.get('architecture') != 'amd64' or not lock.get('source'):
        raise ValueError('锁文件必须声明 version=1、amd64、快照来源')
    records = lock.get('packages', [])
    if not records or len(records) > 256:
        raise ValueError('包数量无效')
    checked = []
    for record in records:
        name = record['file']
        if Path(name).name != name or not name.endswith('.deb'):
            raise ValueError('包必须是包目录下的 .deb 文件名')
        path = packages / name
        if path.is_symlink() or digest(path) != record['sha256']:
            raise ValueError(f'包摘要不符: {name}')
        for key, field in [('package', 'Package'), ('version', 'Version'), ('architecture', 'Architecture')]:
            actual = subprocess.check_output(['dpkg-deb', '-f', str(path), field], text=True).strip()
            if actual != record[key] or key == 'architecture' and actual not in ('amd64', 'all'):
                raise ValueError(f'包元数据不符: {name}/{field}')
        checked.append(path)
    # 不复用、覆盖或递归删除用户目录；失败留下可检查的独立构建目录。
    output.mkdir(mode=0o755, parents=False, exist_ok=False)
    root = output / 'rootfs'
    root.mkdir()
    for package in checked:
        subprocess.run(['dpkg-deb', '-x', str(package), str(root)], check=True)
    apply_layout(root, lock.get('layout'))
    manifest = seal(root, lock['source'] + '; lock-sha256=' + digest(lock_path))
    target = output / 'manifest.json'
    target.write_text(json.dumps(manifest, sort_keys=True, separators=(',', ':')) + '\n')
    shutil.copyfile(lock_path, output / 'packages.lock.json')
    print('ManifestSHA256=' + digest(target))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lock', type=Path, required=True)
    parser.add_argument('--packages', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    build(args.lock, args.packages, args.output)
