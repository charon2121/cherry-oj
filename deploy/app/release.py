#!/usr/bin/env python3
"""从指定提交构建应用发布，或离线核验待部署归档。"""

from __future__ import annotations

import argparse
import json
import os
import signal
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path, PurePosixPath

from artifacts import JARS, OPS, SOURCE_SHA, Manifest, artifact_digest, digest, unpack


ROOT = Path(__file__).resolve().parents[2]


def command(argv: list[str], cwd: Path, log: Path, timeout: int = 1200) -> None:
    environment = {key: os.environ[key] for key in (
        'PATH', 'HOME', 'JAVA_HOME', 'DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_CONFIG', 'DOCKER_CERT_PATH',
        'DOCKER_TLS_VERIFY', 'TMPDIR') if key in os.environ}
    environment.update(LANG='C.UTF-8', TZ='UTC')
    with log.open('xb') as output:
        log.chmod(0o600)
        process = subprocess.Popen(argv, cwd=cwd, env=environment, stdout=output, stderr=output, start_new_session=True)
        try:
            if process.wait(timeout=timeout):
                raise RuntimeError('构建命令失败，详见 ' + str(log))
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()


def snapshot(repo: Path, ref: str, output: Path) -> str:
    sha = subprocess.check_output(['git', 'rev-parse', '--verify', '--end-of-options', ref + '^{commit}'],
                                  cwd=repo, text=True, timeout=15).strip()
    if not SOURCE_SHA.fullmatch(sha):
        raise ValueError('不能解析提交身份')
    output.mkdir()
    error = output.parent / 'git-archive.log'
    archive_path = output.parent / (output.name + '.tar')
    with error.open('xb') as errors, archive_path.open('xb') as contents:
        subprocess.run(['git', 'archive', '--format=tar', sha], cwd=repo, stdout=contents,
                       stderr=errors, timeout=60, check=True)
    with tarfile.open(archive_path, 'r:') as archive:
        for member in archive:
            path = PurePosixPath(member.name)
            if (path.is_absolute() or any(part in ('', '.', '..') for part in member.name.rstrip('/').split('/'))
                    or '\\' in member.name or not (member.isdir() or member.isfile())):
                raise ValueError('源码归档含不支持的路径、链接或特殊文件')
            target = output / path
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            content = archive.extractfile(member)
            if content is None:
                raise ValueError('源码归档缺少文件内容')
            with content, target.open('xb') as destination:
                shutil.copyfileobj(content, destination, length=65536)
            target.chmod(0o755 if member.mode & 0o111 else 0o644)
    archive_path.unlink()
    return sha


def source_files(root: Path) -> dict[str, str]:
    return {path.relative_to(root).as_posix(): digest(path) for path in root.rglob('*') if path.is_file()}


def package(source_sha: str, inputs: dict[str, Path], output: Path) -> dict[str, str | int]:
    for path in inputs.values():
        if path.is_symlink() or not path.is_file():
            raise ValueError('构建产物不是普通文件: ' + str(path))
    files = {name: digest(path) for name, path in sorted(inputs.items())}
    collection = artifact_digest(files)
    release = f'app-{source_sha[:12]}-{collection[:12]}'
    document = dict(version=1, release=release, sourceSha=source_sha, files=files, artifactSetSha256=collection)
    Manifest.parse(document)
    manifest = output / 'release.json'
    with manifest.open('x') as target:
        json.dump(document, target, indent=2)
        target.write('\n')
    archive = output / 'app.tar.gz.partial'
    with tarfile.open(archive, 'x:gz') as target:
        for name, path in sorted(dict(inputs, **{'release.json': manifest}).items()):
            info = tarfile.TarInfo(name)
            info.size = path.stat().st_size
            info.mode = 0o755 if name == OPS else 0o644
            with path.open('rb') as content:
                target.addfile(info, content)
    expected = digest(archive)
    with tempfile.TemporaryDirectory(prefix='verify-', dir=output) as temporary:
        unpack(archive, expected, Path(temporary) / 'release')
    archive.rename(output / 'app.tar.gz')
    result = dict(version=1, release=release, sourceSha=source_sha, sha256=expected,
                  sizeBytes=(output / 'app.tar.gz').stat().st_size)
    with (output / 'package.json').open('x') as target:
        json.dump(result, target, indent=2)
        target.write('\n')
    return result


def build(repo: Path, ref: str, output: Path) -> dict[str, str | int]:
    output.mkdir(mode=0o700)
    work = output / 'source'
    sha = snapshot(repo, ref, work)
    before = source_files(work)
    java = subprocess.check_output(['java', '-version'], stderr=subprocess.STDOUT, text=True, timeout=15)
    node = subprocess.check_output(['node', '--version'], text=True, timeout=15)
    if 'version "21.' not in java or not node.startswith('v24.'):
        raise ValueError('构建需要 Java 21 与 Node 24')
    steps = (
        ('Java 检查与打包', ['./mvnw', '-B', 'clean', 'verify'], work / 'apps/server', 'java.log'),
        ('Web 安装锁定依赖', ['npm', 'ci'], work / 'apps/web', 'npm-ci.log'),
        ('Web 检查', ['npm', 'run', 'check'], work / 'apps/web', 'web-check.log'),
        ('Web 构建', ['npm', 'run', 'build'], work / 'apps/web', 'web-build.log'),
    )
    for label, argv, cwd, log in steps:
        print(label, flush=True)
        command(argv, cwd, output / log)
    # 只核对最初导出的源码；构建缓存与生成目录不能被当成新输入混进身份。
    if any(not (work / name).is_file() or digest(work / name) != expected for name, expected in before.items()):
        raise ValueError('构建期间源码发生变化，拒绝标记来源提交')
    files = {name: work / 'apps/server' / Path(name).stem / 'target' /
             (Path(name).stem + '-0.0.1-SNAPSHOT.jar') for name in JARS}
    for path in (work / 'apps/web/dist').rglob('*'):
        if path.is_symlink():
            raise ValueError('Web 构建产物不允许符号链接')
        if path.is_file():
            files['web/' + path.relative_to(work / 'apps/web/dist').as_posix()] = path
    files[OPS] = work / 'scripts/service-identity-keys.sh'
    return package(sha, files, output)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    builder = commands.add_parser('build')
    builder.add_argument('--ref', required=True)
    builder.add_argument('--output', type=Path, required=True)
    inspector = commands.add_parser('inspect')
    inspector.add_argument('--package', type=Path, required=True)
    inspector.add_argument('--sha256', required=True)
    args = parser.parse_args(argv)
    try:
        if args.action == 'build':
            result = build(ROOT, args.ref, args.output)
        else:
            with tempfile.TemporaryDirectory(prefix='cherry-app-inspect-') as temporary:
                manifest = unpack(args.package, args.sha256, Path(temporary) / 'release')
                result = dict(release=manifest.release, sourceSha=manifest.source_sha,
                              artifactSetSha256=manifest.artifact_sha256, files=len(manifest.files))
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError, tarfile.TarError) as error:
        print('应用发布失败: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
