#!/usr/bin/env python3
"""big-output 的输入：n。

用法：gen.py <seed> <输出目录>。"""
import random
import sys
from pathlib import Path

CASES = ["100\n", "50000\n"]


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
