#!/usr/bin/env python3
"""sum-large 的输入：4 个固定的测试点。

用法：gen.py <seed> <输出目录>。"""
import random
import sys
from pathlib import Path

CASES = [
    "3\n1 2 3\n",
    "3\n2000000000 2000000000 2000000000\n",
    "2\n4611686018427387903 4611686018427387903\n",
    "4\n-5 3 -100 20\n",
]


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
