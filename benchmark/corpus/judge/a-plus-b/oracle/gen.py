#!/usr/bin/env python3
"""生成 a-plus-b 的输入：gen.py <seed> <输出目录>。

三个测试点是固定的普通值、含负数、含 0；种子不影响内容，只为满足「同一种子同一程序逐字节相同」的约定。
"""
import sys
from pathlib import Path

CASES = ["3 5\n", "-4 -6\n", "0 0\n"]


def main() -> int:
    seed, out = int(sys.argv[1]), Path(sys.argv[2])
    del seed
    for index, text in enumerate(CASES, start=1):
        (out / f"{index}.in").write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
