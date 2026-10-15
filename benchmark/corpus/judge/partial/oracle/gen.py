#!/usr/bin/env python3
"""partial 的输入：6 个测试点，后三个的 |a| 不小于 10^6。

用法：gen.py <seed> <输出目录>。"""
import random
import sys
from pathlib import Path

CASES = ["1 1\n", "2 3\n", "10 20\n", "1000000 2000000\n", "-5000000 7000000\n", "123456789 987654321\n"]


def cases(rng: random.Random):
    return CASES


def main() -> int:
    seed, out = int(sys.argv[1]), Path(sys.argv[2])
    rng = random.Random(seed)
    for index, text in enumerate(cases(rng), start=1):
        (out / f"{index}.in").write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
