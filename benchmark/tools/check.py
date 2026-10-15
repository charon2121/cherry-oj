#!/usr/bin/env python3
"""不需要 judge 的语料检查：提交前跑一遍，保证语料没有漂移。

    check.py [--corpus 语料根] [--no-regenerate]

1. 语料格式合格（协议 §2、§3），预期自洽；
2. `corpus.lock` 与每题 testdata.json 的指纹一致；
3. 默认还会用 `oracle/` 重新生成每道题的数据，与入库的逐字节比对（`--no-regenerate` 跳过）。
退出码：0 通过；1 有问题。
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import corpus as corpus_lib  # noqa: E402
import generate  # noqa: E402


def check(root: Path, regenerate: bool = True) -> list[str]:
    try:
        corpus = corpus_lib.load_corpus(root)
    except corpus_lib.CorpusError as error:
        return [str(error)]
    problems = corpus_lib.verify_lock(corpus)
    if regenerate:
        for name, problem in corpus.problems.items():
            try:
                scratch = generate.regenerate(problem.directory, problem.seed)
            except generate.GenerateError as error:
                problems.append(f"{name}：重新生成失败：{error}")
                continue
            try:
                if generate.tree_bytes(scratch) != generate.tree_bytes(problem.testdata):
                    problems.append(f"{name}：重新生成的数据与入库的不一致（数据漂移）")
            finally:
                shutil.rmtree(scratch.parent, ignore_errors=True)
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--corpus", type=Path, default=generate.DEFAULT_CORPUS)
    parser.add_argument("--no-regenerate", action="store_true")
    args = parser.parse_args(argv)
    problems = check(args.corpus, not args.no_regenerate)
    for line in problems:
        print(f"✗ {line}", file=sys.stderr)
    if problems:
        return 1
    print("✓ 语料合格，与 corpus.lock 一致" + ("" if args.no_regenerate else "，重新生成的数据逐字节相同"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
