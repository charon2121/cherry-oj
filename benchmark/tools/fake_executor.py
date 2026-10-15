"""测试用的假执行器：按执行器协议读 box、写事实，行为由「源码」第一行的标记决定。不是测试文件（不以 _test 结尾）。

用 `install(directory, boxes)` 在 directory 里写出一个可执行的假执行器和它的受信配置。被测的是 benchmark 自己对协议的实现：
假执行器像真的一样挑剔——stdin 必须是管道、box 里的文件必须属于调用者、请求必须以 NUL 结尾，不满足就拒绝（退出码 1）。

「程序」的内容就是它的源码，第一行写 `// FAKE {json}`：
    facts      覆盖事实里的字段
    stdout     写进 out/stdout 的文本
    stderr     写进 out/stderr 的文本
    sleep      运行前睡多少秒
    exit       执行器自己的退出码（2 表示回收未确认）
    leftover   在 out/ 里多留一个协议没有规定的文件
编译（命令 g++）把输入 0 原样当产物；源码含 COMPILE_ERROR 则编译失败。
"""

from __future__ import annotations

import json
import os
import stat
import sys
from pathlib import Path

SCRIPT = r'''#!{python}
import json, os, stat, sys, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
SETTINGS = json.loads((HERE / "fake-executor.json").read_text())


def refuse(message):
    sys.stderr.write("sandbox: " + message + "\n")
    sys.exit(1)


if len(sys.argv) != 3 or sys.argv[1] != "--box":
    refuse("usage: sandbox --box N")
index = int(sys.argv[2])
box = Path(SETTINGS["boxes"]) / str(index)
if not stat.S_ISFIFO(os.fstat(0).st_mode):
    refuse("stdin must be a pipe")
if set(os.environ) - {"__CF_USER_TEXT_ENCODING", "LC_CTYPE", "PYTHONUTF8"}:  # macOS/Python 自己加的几项除外
    refuse("environment must be empty")
for path in (box, box / "in", box / "out", box / "spec", box / "stdin"):
    info = path.lstat()
    if info.st_uid != os.getuid() or info.st_mode & 0o022:
        refuse("box " + str(path) + " missing or not owned by the service")
if any((box / "out").iterdir()):
    refuse("out directory is not empty")
data = (box / "spec").read_bytes()
if not data.endswith(b"\0"):
    refuse("spec is not NUL terminated")
spec = {}
for record in data[:-1].split(b"\0"):
    key, separator, value = record.decode().partition("=")
    if not separator:
        refuse("malformed spec record")
    spec.setdefault(key, []).append(value)
for key in ("cpu_ns", "clock_ns", "memory_bytes", "max_processes", "stdout_max_bytes", "stderr_max_bytes"):
    if len(spec.get(key, [])) != 1 or int(spec[key][0]) <= 0:
        refuse("missing limit " + key)
with open(SETTINGS["calls"], "a") as stream:
    stream.write(json.dumps({"box": index, "arg": spec["arg"], "input": spec.get("input", []),
                             "limits": {k: int(v[0]) for k, v in spec.items() if k.endswith(("_ns", "_bytes", "_processes"))},
                             "stdin": (box / "stdin").read_text(errors="replace")}) + "\n")

facts = {"version": 1, "exitCode": 0, "signal": 0, "cpuNs": 1000000, "memoryBytes": 1048576, "clockNs": 2000000,
         "reason": "", "oom": 0, "oomKill": 0, "memoryMaxEvents": 0, "pidsMaxEvents": 0, "cancelled": False,
         "outputExceeded": False, "stdoutBytes": 0, "stderrBytes": 0, "outputs": [], "error": ""}
stdout = stderr = ""
code = 0
if spec["arg"][0] == "g++":
    source = (box / "in" / "0").read_text()
    if "COMPILE_ERROR" in source:
        facts["exitCode"], stderr = 1, "Main.cpp: error: COMPILE_ERROR"
    else:
        (box / "out" / "artifact-0").write_bytes((box / "in" / "0").read_bytes())
        (box / "out" / "artifact-0").chmod(0o600)
        facts["outputs"] = [{"index": 0, "path": "Main", "sizeBytes": len(source)}]
else:
    program = (box / "in" / "0").read_text()
    first = program.splitlines()[0] if program else ""
    behaviour = json.loads(first.split("FAKE", 1)[1]) if first.startswith("// FAKE") else {}
    time.sleep(behaviour.get("sleep", 0))
    facts.update(behaviour.get("facts", {}))
    stdout, stderr = behaviour.get("stdout", ""), behaviour.get("stderr", "")
    code = behaviour.get("exit", 0)
    if behaviour.get("leftover"):
        (box / "out" / "unexpected").write_text("x")
facts["stdoutBytes"], facts["stderrBytes"] = len(stdout), len(stderr)
for name, text in (("stdout", stdout), ("stderr", stderr)):
    (box / "out" / name).write_text(text)
    (box / "out" / name).chmod(0o600)
if code:
    refuse("box could not be reclaimed") if code == 1 else (sys.stderr.write("sandbox: unreclaimed\n"), sys.exit(code))
print(json.dumps(facts))
'''


def install(directory: Path, boxes: Path, box_count: int = 2) -> tuple[Path, Path]:
    """返回 (假执行器, 受信配置)。"""
    directory.mkdir(parents=True, exist_ok=True)
    binary = directory / "sandbox"
    binary.write_text(SCRIPT.replace("{python}", sys.executable))
    binary.chmod(0o755)
    (directory / "fake-executor.json").write_text(json.dumps({"boxes": str(boxes), "calls": str(directory / "calls.jsonl")}))
    boxes.mkdir(parents=True, exist_ok=True)
    for index in range(box_count):
        (boxes / str(index)).mkdir(exist_ok=True, mode=0o700)
    uid = os.getuid()
    config = directory / "executor.conf"
    config.write_text("\n".join([
        f"rootfs={directory / 'rootfs'}", f"boxes={boxes}", f"cgroup={directory / 'jobs'}", f"box_count={box_count}",
        f"service_uid={uid}", f"service_gid={os.getgid()}", "payload_uid=61100", "payload_gid=61100",
        "init_uid=61400", "init_gid=61400"]) + "\n")
    (directory / "jobs").mkdir(exist_ok=True)
    return binary, config


def program(**behaviour: object) -> str:
    """一段假「源码」：第一行描述它运行时的行为。"""
    return f"// FAKE {json.dumps(behaviour)}\nint main() {{}}\n"


def calls(directory: Path) -> list[dict]:
    path = directory / "calls.jsonl"
    return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []
