#!/usr/bin/env python3
"""sort-numbers 的输入：5 个测试点，规模与形态见 README。

用法：gen.py <seed> <输出目录>。"""
import random
import sys
from pathlib import Path



def line(values) -> str:
    return " ".join(map(str, values))


def cases(rng: random.Random):
    small = [42]
    duplicated = [5, 3, 5, 1, 3, 9, 1, 5, 2, 9]
    reversed_distinct = list(range(1000, 0, -1))
    many_duplicates = [rng.randint(0, 9999) for _ in range(100_000)]
    all_equal = [7] * 100_000
    for values in (small, duplicated, reversed_distinct, many_duplicates, all_equal):
        yield f"{len(values)}\n{line(values)}\n"


def main() -> int:
    seed, out = int(sys.argv[1]), Path(sys.argv[2])
    rng = random.Random(seed)
    for index, text in enumerate(cases(rng), start=1):
        (out / f"{index}.in").write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
