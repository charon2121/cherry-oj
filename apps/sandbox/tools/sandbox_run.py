#!/usr/bin/env python3
"""在 sandbox 执行器里运行一条命令：一条命令行搞定，不用手工准备 box 目录和请求文件。

    sudo sandbox_run.py --stdin in.txt -- ./prog
    sudo sandbox_run.py --put main.cpp --get Main -- g++ main.cpp -o Main -O2 -std=c++17
    sudo sandbox_run.py --put Main:Main:x --stdin in.txt -- Main

命令的标准输出/标准错误原样输出到本进程的标准输出/标准错误，最后一行摘要写到标准错误。
退出码：0 命令正常结束；1 命令没有正常结束（非零退出、信号、超限、取消、平台故障）；2 本工具或执行器拒绝了请求。
协议细节见 docs/sandbox-executor.md。只用标准库。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence

DEFAULT_SANDBOX = Path("/opt/cherry-oj/current/libexec/sandbox")
DEFAULT_CONFIG = Path("/opt/cherry-oj/etc/executor.conf")

# 与执行器（apps/sandbox/src/spec.c）一致：路径最多 8 段，每段字母数字开头，不以点开头。
PATH_SEGMENT = r"[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"
WORKSPACE_PATH = re.compile(rf"{PATH_SEGMENT}(/{PATH_SEGMENT}){{0,7}}")
COMMAND_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.+-]{0,127}")
ENV_ASSIGNMENT = re.compile(r"[^=\0]+=[^\0]*")

MAX_BOX_INDEX = 3

# 执行器只跑在 Linux x86_64 上；信号名用固定表，不随本工具所在系统变化。
LINUX_SIGNALS = {1: "SIGHUP", 2: "SIGINT", 3: "SIGQUIT", 4: "SIGILL", 6: "SIGABRT", 7: "SIGBUS", 8: "SIGFPE",
                 9: "SIGKILL", 11: "SIGSEGV", 13: "SIGPIPE", 14: "SIGALRM", 15: "SIGTERM", 24: "SIGXCPU",
                 25: "SIGXFSZ", 31: "SIGSYS（违反 seccomp）"}


class UsageError(Exception):
    """请求本身不对（或环境不满足）：退出码 2，消息直接给用户看。"""


@dataclass(frozen=True)
class Limits:
    cpu_ns: int
    clock_ns: int
    memory_bytes: int
    max_processes: int
    stdout_max_bytes: int
    stderr_max_bytes: int


@dataclass(frozen=True)
class Put:
    source: Path
    dest: str
    executable: bool


@dataclass(frozen=True)
class Get:
    path: str
    local: Path


@dataclass(frozen=True)
class Config:
    boxes: Path
    service_uid: int
    service_gid: int
    box_count: int


def parse_duration(text: str) -> int:
    """`500ms`、`2s`、`1.5s` → 纳秒。"""
    match = re.fullmatch(r"(\d+(?:\.\d+)?)(ms|s)", text)
    if not match:
        raise UsageError(f"时间写法不对：{text!r}，应像 500ms 或 2s")
    scale = 1_000_000 if match.group(2) == "ms" else 1_000_000_000
    return round(float(match.group(1)) * scale)


def parse_size(text: str) -> int:
    """`64k`、`256m`、`1g` 或纯数字（字节）→ 字节，k/m/g 按 1024。"""
    match = re.fullmatch(r"(\d+)([kKmMgG]?)", text)
    if not match:
        raise UsageError(f"大小写法不对：{text!r}，应像 64k、256m 或纯数字（字节）")
    scale = {"": 1, "k": 1 << 10, "m": 1 << 20, "g": 1 << 30}[match.group(2).lower()]
    return int(match.group(1)) * scale


def parse_put(text: str) -> Put:
    """`SRC`、`SRC:DEST`、`SRC:DEST:x`（x 表示可执行）。"""
    parts = text.split(":")
    executable = len(parts) == 3 and parts[2] == "x"
    if len(parts) > 3 or (len(parts) == 3 and not executable):
        raise UsageError(f"--put 写法不对：{text!r}，应像 SRC、SRC:DEST 或 SRC:DEST:x")
    source = Path(parts[0])
    dest = parts[1] if len(parts) > 1 and parts[1] else source.name
    if not WORKSPACE_PATH.fullmatch(dest):
        raise UsageError(f"工作区里的名字不合法：{dest!r}（字母数字开头，只含字母数字和 _ . -，最多 8 段）")
    return Put(source, dest, executable)


def parse_get(text: str) -> Get:
    """`PATH` 或 `PATH=LOCAL`。"""
    path, _, local = text.partition("=")
    if not WORKSPACE_PATH.fullmatch(path):
        raise UsageError(f"工作区里的名字不合法：{path!r}")
    return Get(path, Path(local or path.rsplit("/", 1)[-1]))


def load_config(path: Path) -> Config:
    """只读取调用方需要的几项：box 根目录、服务身份、box 数量。"""
    try:
        text = path.read_text()
    except OSError as error:
        raise UsageError(f"读不到执行器配置 {path}：{error.strerror}（执行器是否已安装？）") from error
    values = dict(line.split("=", 1) for line in text.splitlines() if "=" in line and not line.startswith("#"))
    try:
        return Config(
            boxes=Path(values["boxes"]),
            service_uid=int(values["service_uid"]),
            service_gid=int(values["service_gid"]),
            box_count=int(values["box_count"]),
        )
    except (KeyError, ValueError) as error:
        raise UsageError(f"执行器配置 {path} 缺少或写错了 boxes/service_uid/service_gid/box_count") from error


def build_spec(command: Sequence[str], env: Sequence[str], puts: Sequence[Put], gets: Sequence[Get], limits: Limits) -> bytes:
    """请求格式：以 NUL 结尾的 key=value 记录（见 docs/sandbox-executor.md）。"""
    if not command or not COMMAND_NAME.fullmatch(command[0]):
        raise UsageError(f"命令必须是裸名称（不带目录）：{command[0] if command else ''!r}。"
                         "要运行自己的程序，先用 --put 放进工作区，再用名字调用")
    for assignment in env:
        if not ENV_ASSIGNMENT.fullmatch(assignment):
            raise UsageError(f"--env 写法不对：{assignment!r}，应像 KEY=VALUE")
    records = [f"arg={argument}" for argument in command]
    records += [f"env={assignment}" for assignment in env]
    records += [f"input={int(put.executable)}:{put.dest}" for put in puts]
    records += [f"output={get.path}" for get in gets]
    records += [
        f"cpu_ns={limits.cpu_ns}",
        f"clock_ns={limits.clock_ns}",
        f"memory_bytes={limits.memory_bytes}",
        f"max_processes={limits.max_processes}",
        f"stdout_max_bytes={limits.stdout_max_bytes}",
        f"stderr_max_bytes={limits.stderr_max_bytes}",
    ]
    if any("\0" in record for record in records):
        raise UsageError("参数里不能含 NUL 字符")
    return b"".join(record.encode() + b"\0" for record in records)


def prepare_box(config: Config, index: int, spec: bytes, stdin: bytes, puts: Sequence[Put]) -> Path:
    """把 box 复位成干净状态并写好请求。只会删除 <boxes>/<index> 这一个目录。"""
    if not 0 <= index < config.box_count or index > MAX_BOX_INDEX:
        raise UsageError(f"box 编号 {index} 超出范围（本机配置了 {config.box_count} 个 box，编号从 0 起）")
    if os.geteuid() not in (0, config.service_uid):
        raise UsageError("需要以 root 或执行器的服务用户运行（例如 sudo）")
    box = config.boxes / str(index)
    shutil.rmtree(box, ignore_errors=True)
    for directory in (box, box / "in", box / "out"):
        directory.mkdir(mode=0o700)
        own(directory, config, 0o700)
    for position, put in enumerate(puts):
        write_private(box / "in" / str(position), read_source(put.source), config)
    write_private(box / "spec", spec, config)
    write_private(box / "stdin", stdin, config)
    return box


def read_source(path: Path) -> bytes:
    try:
        return path.read_bytes()
    except OSError as error:
        raise UsageError(f"读不到要放进工作区的文件 {path}：{error.strerror}") from error


def write_private(path: Path, data: bytes, config: Config) -> None:
    path.write_bytes(data)
    own(path, config, 0o600)


def own(path: Path, config: Config, mode: int) -> None:
    """执行器要求 box 里的一切都属于服务身份；root 运行时在这里交出所有权。"""
    if os.geteuid() == 0:
        os.chown(path, config.service_uid, config.service_gid)
    path.chmod(mode)


def run_executor(sandbox: Path, index: int) -> tuple[int, bytes, str]:
    """运行执行器。stdin 是一根保持打开的管道：关闭它就是取消（Ctrl-C 时我们主动关闭）。"""
    process = subprocess.Popen(
        [str(sandbox), "--box", str(index)],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env={},
    )
    assert process.stdin is not None and process.stdout is not None and process.stderr is not None
    try:
        facts = process.stdout.read()
        diagnostics = process.stderr.read()
    except KeyboardInterrupt:
        process.stdin.close()  # 取消：执行器会整组回收并如实报告
        facts = process.stdout.read()
        diagnostics = process.stderr.read()
    code = process.wait()
    if not process.stdin.closed:
        process.stdin.close()
    return code, facts, diagnostics.decode(errors="replace").strip()


def classify(facts: dict[str, Any], limits: Limits) -> tuple[str, bool]:
    """把事实归成一句话；返回 (说明, 是否正常结束)。判断顺序与 judge 一致。"""
    reason = facts.get("reason", "")
    if facts.get("error") or reason == "platform":
        return f"平台故障：{facts.get('error') or '未知'}", False
    if facts.get("oom", 0) > 0 and facts.get("oomKill", 0) > 0:
        return "内存超限", False
    if reason in ("cpu", "wall") or facts.get("cpuNs", 0) >= limits.cpu_ns:
        return ("CPU 时间超限" if reason != "wall" else "墙钟超限"), False
    if reason == "output" or facts.get("outputExceeded"):
        return "输出超限", False
    if reason == "cancelled" or facts.get("cancelled"):
        return "已取消", False
    if facts.get("signal"):
        number = facts["signal"]
        return f"被信号 {number}（{LINUX_SIGNALS.get(number, '?')}）终止", False
    if facts.get("exitCode", 0) == 0:
        return "正常结束", True
    return f"退出码 {facts['exitCode']}", False


def summary(label: str, facts: dict[str, Any]) -> str:
    return (f"[sandbox] {label}  cpu={facts.get('cpuNs', 0) / 1e6:.1f}ms  "
            f"mem={facts.get('memoryBytes', 0) / (1 << 20):.1f}MiB  wall={facts.get('clockNs', 0) / 1e6:.0f}ms")


def read_result(box: Path, name: str) -> bytes:
    path = box / "out" / name
    return path.read_bytes() if path.exists() else b""


def deliver_artifacts(box: Path, facts: dict[str, Any], gets: Sequence[Get]) -> None:
    delivered = {item["path"]: item["index"] for item in facts.get("outputs", [])}
    for position, get in enumerate(gets):
        if get.path not in delivered:
            print(f"[sandbox] 工作区里没有生成 {get.path}", file=sys.stderr)
            continue
        get.local.write_bytes((box / "out" / f"artifact-{delivered[get.path]}").read_bytes())
        print(f"[sandbox] 已取回 {get.path} → {get.local}", file=sys.stderr)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=__doc__.split("\n\n")[0],
        usage="%(prog)s [选项] -- 命令 [参数...]",
        epilog="命令必须是裸名称，在工作区 /work、/usr/bin、/bin 里找；自己的程序先用 --put 放进工作区。",
    )
    parser.add_argument("--stdin", type=Path, help="标准输入来自这个文件（默认为空）")
    parser.add_argument("--put", action="append", default=[], metavar="SRC[:DEST[:x]]",
                        help="把本地文件放进工作区；DEST 默认取文件名；末尾 :x 表示可执行。可重复")
    parser.add_argument("--get", action="append", default=[], metavar="PATH[=LOCAL]",
                        help="执行后把工作区里的 PATH 取回，保存为 LOCAL（默认同名）。可重复")
    parser.add_argument("--env", action="append", default=[], metavar="KEY=VALUE", help="环境变量。可重复")
    parser.add_argument("--cpu", default="2s", help="CPU 时间上限，如 500ms、2s（默认 2s，最大 60s）")
    parser.add_argument("--wall", default="10s", help="墙钟上限（默认 10s，最大 120s）")
    parser.add_argument("--mem", default="256m", help="内存上限，如 64m、1g（默认 256m，最大 1g）")
    parser.add_argument("--procs", type=int, default=32, help="进程数上限（默认 32，最大 256）")
    parser.add_argument("--out", default="1m", help="标准输出保留上限（默认 1m，最大 1m）")
    parser.add_argument("--err", default="64k", help="标准错误保留上限（默认 64k，最大 1m）")
    parser.add_argument("--box", type=int, default=0, help="使用第几个 box（默认 0）；同一个 box 不能同时用两次")
    parser.add_argument("--sandbox", type=Path, default=DEFAULT_SANDBOX, help=f"执行器路径（默认 {DEFAULT_SANDBOX}）")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG, help=f"执行器配置（默认 {DEFAULT_CONFIG}）")
    parser.add_argument("--json", action="store_true", help="把执行器返回的事实 JSON 另外打印到标准错误")
    parser.add_argument("command", nargs=argparse.REMAINDER, metavar="-- 命令 [参数...]")
    return parser


def run(arguments: Sequence[str]) -> int:
    parser = build_parser()
    options = parser.parse_args(arguments)
    command = options.command[1:] if options.command[:1] == ["--"] else options.command
    limits = Limits(
        cpu_ns=parse_duration(options.cpu),
        clock_ns=parse_duration(options.wall),
        memory_bytes=parse_size(options.mem),
        max_processes=options.procs,
        stdout_max_bytes=parse_size(options.out),
        stderr_max_bytes=parse_size(options.err),
    )
    puts = [parse_put(text) for text in options.put]
    gets = [parse_get(text) for text in options.get]
    spec = build_spec(command, options.env, puts, gets, limits)
    stdin = read_source(options.stdin) if options.stdin else b""
    config = load_config(options.config)
    box = prepare_box(config, options.box, spec, stdin, puts)
    try:
        code, raw, diagnostics = run_executor(options.sandbox, options.box)
        if code == 1:
            raise UsageError(f"执行器拒绝了请求：{diagnostics}")
        if code != 0:
            print(f"[sandbox] 执行器退出码 {code}：执行组可能没有回收干净，这个 box 先不要再用。{diagnostics}", file=sys.stderr)
            return 2
        facts = json.loads(raw)
        sys.stdout.buffer.write(read_result(box, "stdout"))
        sys.stdout.buffer.flush()
        sys.stderr.buffer.write(read_result(box, "stderr"))
        sys.stderr.buffer.flush()
        deliver_artifacts(box, facts, gets)
        label, normal = classify(facts, limits)
        print(summary(label, facts), file=sys.stderr)
        if options.json:
            print(json.dumps(facts, ensure_ascii=False), file=sys.stderr)
        return 0 if normal else 1
    finally:
        shutil.rmtree(box, ignore_errors=True)  # 用完即清：源码、输入和产物不在磁盘上多停留


def main() -> int:
    try:
        return run(sys.argv[1:])
    except UsageError as error:
        print(f"sandbox_run: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
