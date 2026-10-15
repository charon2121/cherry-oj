"""直接调用 sandbox 执行器（执行器协议见 docs/protocols/sandbox-executor.md）。

这是 benchmark 自己对协议的一份独立实现：准备 box 目录、写 NUL 分隔的请求、保持 stdin 管道到执行器退出（取消协议）、
严格解析事实 JSON。运行者必须是被测节点本机上的 root 或执行器的服务身份。
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from corpus_base import CorpusError  # noqa: F401  （方便调用方统一捕获语料类错误）

CONFIG_KEYS = frozenset({"rootfs", "boxes", "cgroup", "box_count", "service_uid", "service_gid", "payload_uid",
                         "payload_gid", "init_uid", "init_gid"})
FACT_TYPES: dict[str, type] = {
    "version": int, "exitCode": int, "signal": int, "cpuNs": int, "memoryBytes": int, "clockNs": int,
    "reason": str, "oom": int, "oomKill": int, "memoryMaxEvents": int, "pidsMaxEvents": int, "cancelled": bool,
    "outputExceeded": bool, "stdoutBytes": int, "stderrBytes": int, "outputs": list, "error": str}
REASONS = frozenset({"", "cpu", "wall", "output", "cancelled", "platform"})
DEFAULT_PROCESSES, DEFAULT_OUTPUT = 64, 1 << 20
# 编译限制与官方安装的 judge 一致（协议 §6）。
COMPILE_LIMITS = {"cpuNs": 10_000_000_000, "clockNs": 20_000_000_000, "memoryBytes": 256 << 20, "maxProcesses": 64,
                  "stdoutMaxBytes": 1 << 20, "stderrMaxBytes": 1 << 20}
COMPILE_COMMANDS = {"cpp": (["g++", "Main.cpp", "-o", "Main", "-O2", "-std=c++17"], "Main.cpp")}
KEYS = {"cpuNs": "cpu_ns", "clockNs": "clock_ns", "memoryBytes": "memory_bytes", "maxProcesses": "max_processes",
        "stdoutMaxBytes": "stdout_max_bytes", "stderrMaxBytes": "stderr_max_bytes"}


class ExecutorError(Exception):
    """环境或协议层面的问题（连不上、配置读不到、事实 JSON 不合法），不是某个样本的结论。"""


@dataclass(frozen=True)
class ExecutorConfig:
    rootfs: Path
    boxes: Path
    cgroup: Path
    box_count: int
    service_uid: int
    service_gid: int
    payload_uid: int
    payload_gid: int
    init_uid: int
    init_gid: int


def load_config(path: Path) -> ExecutorConfig:
    try:
        text = path.read_text()
    except OSError as error:
        raise ExecutorError(f"读不到执行器配置 {path}：{error.strerror}（执行器是否已安装？）") from error
    values: dict[str, str] = {}
    for line in text.splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    missing = sorted(CONFIG_KEYS - set(values))
    if missing:
        raise ExecutorError(f"执行器配置 {path} 缺少 {', '.join(missing)}")
    try:
        return ExecutorConfig(
            rootfs=Path(values["rootfs"]), boxes=Path(values["boxes"]), cgroup=Path(values["cgroup"]),
            **{key: int(values[key]) for key in CONFIG_KEYS - {"rootfs", "boxes", "cgroup"}})
    except ValueError as error:
        raise ExecutorError(f"执行器配置 {path} 里的数值不对：{error}") from error


@dataclass
class Call:
    """一次执行器调用的全部观测。"""

    exit_code: int
    facts: dict[str, Any] | None
    refusal: str  # 执行器 stderr（退出码非 0 时的拒绝原因）
    stdout: bytes
    stderr: bytes
    artifacts: dict[str, bytes]
    started: float
    ended: float
    leftovers: list[str] = field(default_factory=list)

    @property
    def elapsed_ms(self) -> float:
        return (self.ended - self.started) * 1000


def spec_bytes(argv: list[str], limits: dict[str, int], inputs: list[tuple[str, bool]], outputs: list[str]) -> bytes:
    """请求：以 NUL 结尾的 key=value 记录（协议 §5）。"""
    records = [f"arg={argument}" for argument in argv]
    records += [f"input={int(executable)}:{destination}" for destination, executable in inputs]
    records += [f"output={path}" for path in outputs]
    records += [f"{KEYS[name]}={limits[name]}" for name in KEYS]
    if any("\0" in record for record in records):
        raise ExecutorError("请求里不能含 NUL 字符")
    return b"".join(record.encode() + b"\0" for record in records)


def parse_facts(data: bytes) -> dict[str, Any]:
    """严格解析事实 JSON：未知字段、未知 version、未知 reason 都是错误（执行器协议 §8）。"""
    try:
        facts = json.loads(data)
    except ValueError as error:
        raise ExecutorError(f"事实不是合法的 JSON：{error}") from error
    if not isinstance(facts, dict):
        raise ExecutorError("事实必须是 JSON 对象")
    unknown = sorted(set(facts) - set(FACT_TYPES))
    if unknown:
        raise ExecutorError(f"事实里有不认识的字段：{', '.join(unknown)}")
    missing = sorted(set(FACT_TYPES) - set(facts))
    if missing:
        raise ExecutorError(f"事实缺少字段：{', '.join(missing)}")
    for name, kind in FACT_TYPES.items():
        value = facts[name]
        if not isinstance(value, kind) or (kind is int and isinstance(value, bool)):
            raise ExecutorError(f"事实 {name} 的类型应是 {kind.__name__}，收到 {value!r}")
    if facts["version"] != 1:
        raise ExecutorError(f"事实的 version 是 {facts['version']}，只认识 1")
    if facts["reason"] not in REASONS:
        raise ExecutorError(f"事实里有不认识的 reason：{facts['reason']!r}")
    return facts


class Executor:
    def __init__(self, binary: Path, config: ExecutorConfig) -> None:
        self.binary, self.config = binary, config
        if os.geteuid() not in (0, config.service_uid):
            raise ExecutorError("需要以 root 或执行器的服务用户运行（例如 sudo）")
        if not binary.is_file():
            raise ExecutorError(f"执行器 {binary} 不存在")
        if config.box_count < 1:
            raise ExecutorError("执行器配置的 box_count 必须大于 0")

    # ----- box -----

    def _own(self, path: Path, mode: int) -> None:
        if os.geteuid() == 0:
            os.chown(path, self.config.service_uid, self.config.service_gid)
        path.chmod(mode)

    def _write(self, path: Path, data: bytes) -> None:
        path.write_bytes(data)
        self._own(path, 0o600)

    def box(self, index: int) -> Path:
        if not 0 <= index < self.config.box_count:
            raise ExecutorError(f"box {index} 超出执行器配置的范围（box_count={self.config.box_count}）")
        return self.config.boxes / str(index)

    def reset(self, index: int) -> None:
        """把 box 复位成干净状态：没有 spec 与 stdin，in/ 与 out/ 为空。只触碰 <boxes>/<index>。"""
        box = self.box(index)
        shutil.rmtree(box, ignore_errors=True)
        for directory in (box, box / "in", box / "out"):
            directory.mkdir(mode=0o700)
            self._own(directory, 0o700)

    def clear(self, index: int) -> None:
        """调用之后的清理：删掉请求与结果，留下空的 in/ 与 out/（judge 期望的静止状态）。"""
        box = self.box(index)
        for name in ("spec", "stdin"):
            (box / name).unlink(missing_ok=True)
        for directory in (box / "in", box / "out"):
            if directory.is_dir():
                for path in directory.iterdir():
                    path.unlink()

    # ----- 调用 -----

    def call(self, index: int, argv: list[str], limits: dict[str, int], stdin: bytes = b"",
             inputs: list[tuple[str, bytes, bool]] | None = None, outputs: list[str] | None = None,
             barrier: threading.Barrier | None = None) -> Call:
        """在 box 里运行一条命令。`barrier` 用于并发组：所有成员准备好之后同时启动。"""
        inputs, outputs = inputs or [], outputs or []
        self.reset(index)
        box = self.box(index)
        for position, (_, data, _) in enumerate(inputs):
            self._write(box / "in" / str(position), data)
        self._write(box / "spec", spec_bytes(argv, limits, [(d, x) for d, _, x in inputs], outputs))
        self._write(box / "stdin", stdin)
        if barrier is not None:
            barrier.wait(timeout=60)
        started = time.monotonic()
        process = subprocess.Popen([str(self.binary), "--box", str(index)], stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, env={})
        assert process.stdin and process.stdout and process.stderr
        captured: dict[str, bytes] = {}

        def drain(name: str, stream: Any) -> None:
            captured[name] = stream.read()

        readers = [threading.Thread(target=drain, args=(name, stream))
                   for name, stream in (("facts", process.stdout), ("refusal", process.stderr))]
        for reader in readers:
            reader.start()
        # stdin 是一根保持打开的管道：关闭它就是取消，所以要等执行器退出之后才能关。
        code = process.wait()
        ended = time.monotonic()
        process.stdin.close()
        for reader in readers:
            reader.join()
        process.stdout.close()
        process.stderr.close()
        facts = parse_facts(captured["facts"]) if code == 0 else None
        result = Call(code, facts, captured["refusal"].decode(errors="replace").strip(),
                      self._read(box, "stdout"), self._read(box, "stderr"), {}, started, ended)
        if facts is not None:
            for item in facts["outputs"]:
                result.artifacts[item["path"]] = self._read(box, f"artifact-{item['index']}")
        result.leftovers = self._leftovers(box, len(inputs), facts)
        return result

    @staticmethod
    def _read(box: Path, name: str) -> bytes:
        path = box / "out" / name
        return path.read_bytes() if path.is_file() else b""

    @staticmethod
    def _leftovers(box: Path, inputs: int, facts: dict[str, Any] | None) -> list[str]:
        """box 里执行器协议没有规定会出现的东西：除运行者自己写的与执行器规定写回的以外，都是遗留。"""
        allowed = {"spec", "stdin", "in", "out"}
        extra = [name for name in sorted(os.listdir(box)) if name not in allowed]
        expected_in = {str(i) for i in range(inputs)}
        extra += [f"in/{name}" for name in sorted(os.listdir(box / "in")) if name not in expected_in]
        written = {"stdout", "stderr"} | {f"artifact-{item['index']}" for item in (facts or {"outputs": []})["outputs"]}
        extra += [f"out/{name}" for name in sorted(os.listdir(box / "out")) if name not in written]
        return extra

    # ----- 编译 -----

    def compile(self, source: str, language: str, box: int = 0) -> tuple[bytes | None, Call]:
        """用与 judge 相同的命令编译，返回 (可执行文件, 这次调用)；编译失败时可执行文件为 None。"""
        argv, name = COMPILE_COMMANDS[language]
        done = self.call(box, argv, COMPILE_LIMITS, inputs=[(name, source.encode(), False)], outputs=["Main"])
        binary = done.artifacts.get("Main") if done.facts and done.facts["exitCode"] == 0 and not done.facts["error"] else None
        return binary, done

    def run_binary(self, binary: bytes, box: int, limits: dict[str, int], stdin: bytes,
                   barrier: threading.Barrier | None = None) -> Call:
        return self.call(box, ["Main"], limits, stdin=stdin, inputs=[("Main", binary, True)], barrier=barrier)
