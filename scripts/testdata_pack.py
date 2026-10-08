#!/usr/bin/env python3
"""给一个放着成对 .in/.out 的目录写出 testdata.json，使它成为符合测试数据协议的目录。

协议见 docs/testdata-protocol.md。测试点顺序：名字能解析为整数的按数值升序，其余按字符串序排在后面。
不改动数据文件；已有 testdata.json 时拒绝覆盖，避免悄悄换掉线上正在读取的元数据。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,123}")
MAX_CASES = 1000


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sort_key(name: str) -> tuple[int, int, str]:
    return (0, int(name), name) if name.isdigit() else (1, 0, name)


def testcase_names(directory: Path) -> list[str]:
    inputs = {p.name[:-3] for p in directory.glob("*.in")}
    outputs = {p.name[:-4] for p in directory.glob("*.out")}
    if inputs != outputs:
        unpaired = sorted([f"{n}.out" for n in inputs - outputs] + [f"{n}.in" for n in outputs - inputs])
        raise ValueError(f"以下文件落单，缺少配对的 .in/.out：{', '.join(unpaired)}")
    names = sorted(inputs, key=sort_key)
    if not names:
        raise ValueError(f"{directory} 里没有成对的 .in/.out 文件")
    if len(names) > MAX_CASES:
        raise ValueError(f"测试点 {len(names)} 个，超过协议上限 {MAX_CASES}")
    bad = [n for n in names if not NAME.fullmatch(n)]
    if bad:
        raise ValueError(f"测试点名字不符合协议（{NAME.pattern}）：{', '.join(bad)}")
    return names


def build(directory: Path) -> dict[str, Any]:
    testcases: list[dict[str, Any]] = []
    lines: list[str] = []
    total = 0
    for name in testcase_names(directory):
        entry: dict[str, Any] = {"name": name}
        for field, suffix in (("input", ".in"), ("output", ".out")):
            path = directory / (name + suffix)
            size = path.stat().st_size
            digest = sha256_file(path)
            entry[field] = {"sizeBytes": size, "sha256": digest}
            lines.append(f"{digest}  {name}{suffix}\n")
            total += size
        testcases.append(entry)
    return {
        "schemaVersion": 1,
        "testcaseCount": len(testcases),
        "totalBytes": total,
        "digest": hashlib.sha256("".join(lines).encode()).hexdigest(),
        "testcases": testcases,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="放着 X.in / X.out 的目录")
    args = parser.parse_args()
    target = args.directory / "testdata.json"
    if target.exists():
        print(f"错误：{target} 已存在，拒绝覆盖", file=sys.stderr)
        return 1
    try:
        metadata = build(args.directory)
    except (OSError, ValueError) as error:
        print(f"错误：{error}", file=sys.stderr)
        return 1
    target.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(f"✓ 写入 {target}：{metadata['testcaseCount']} 个测试点，digest {metadata['digest']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
