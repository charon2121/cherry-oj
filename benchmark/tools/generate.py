#!/usr/bin/env python3
"""确定性地生成题目的测试数据，并用两份算法不同的参考解交叉验证期望输出。

    generate.py [--corpus 语料根] [--problem 题名 ...] [--update-lock --reason 原因]

每道题的 `oracle/` 里有：
    gen.py <seed> <目录>   写出 N.in
    solution_a.py          从 stdin 读输入，向 stdout 写期望输出
    solution_b.py          算法与 A 不同的另一份参考解
两份参考解必须逐字节一致，否则这道题不合格，拒绝写出（协议 §2.3）。

数据写入题目的 `testdata/`（连同 testdata.json）。锁定文件 `corpus.lock` 只有显式给 `--update-lock` 与 `--reason`
才会更新：数据漂移必须是有意的、说得出原因的，不能悄悄通过（协议 §2.4）。
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import corpus as corpus_lib  # noqa: E402

DEFAULT_CORPUS = Path(__file__).resolve().parents[1] / "corpus"
TIMEOUT_SECONDS = 60


class GenerateError(Exception):
    pass


def run(command: list[str], stdin: bytes = b"") -> bytes:
    try:
        done = subprocess.run(command, input=stdin, capture_output=True, timeout=TIMEOUT_SECONDS, check=False)
    except subprocess.TimeoutExpired as error:
        raise GenerateError(f"{' '.join(command)} 超过 {TIMEOUT_SECONDS} 秒") from error
    if done.returncode != 0:
        raise GenerateError(f"{' '.join(command)} 退出码 {done.returncode}：{done.stderr.decode(errors='replace')[:500]}")
    return done.stdout


def build_testdata(problem_dir: Path, seed: int, destination: Path) -> None:
    """在 destination（必须是空目录）里写出完整的协议目录。"""
    oracle = problem_dir / "oracle"
    for script in ("gen.py", "solution_a.py", "solution_b.py"):
        if not (oracle / script).is_file():
            raise GenerateError(f"{problem_dir.name}：缺少 oracle/{script}")
    run([sys.executable, str(oracle / "gen.py"), str(seed), str(destination)])
    inputs = sorted(destination.glob("*.in"))
    if not inputs:
        raise GenerateError(f"{problem_dir.name}：gen.py 没有写出任何 .in")
    for path in inputs:
        data = path.read_bytes()
        answer_a = run([sys.executable, str(oracle / "solution_a.py")], data)
        answer_b = run([sys.executable, str(oracle / "solution_b.py")], data)
        if answer_a != answer_b:
            raise GenerateError(f"{problem_dir.name}/{path.name}：两份参考解的输出不一致，这道题不合格")
        path.with_suffix(".out").write_bytes(answer_a)
    metadata = corpus_lib.testdata_pack.build(destination)
    (destination / "testdata.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")


def regenerate(problem_dir: Path, seed: int) -> Path:
    """返回临时目录里重新生成的 testdata；调用方负责删除其父目录。"""
    scratch = Path(tempfile.mkdtemp(prefix="benchmark-generate-"))
    destination = scratch / "testdata"
    destination.mkdir()
    try:
        build_testdata(problem_dir, seed, destination)
    except BaseException:
        shutil.rmtree(scratch, ignore_errors=True)
        raise
    return destination


def tree_bytes(directory: Path) -> dict[str, bytes]:
    return {path.name: path.read_bytes() for path in sorted(directory.iterdir()) if path.is_file()}


def read_seed(problem_dir: Path) -> int:
    meta = corpus_lib.read_json(problem_dir / "problem.json")
    if not isinstance(meta, dict) or isinstance(meta.get("seed"), bool) or not isinstance(meta.get("seed"), int):
        raise GenerateError(f"{problem_dir / 'problem.json'} 的 seed 必须是整数")
    return meta["seed"]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--corpus", type=Path, default=DEFAULT_CORPUS, help="语料根（默认 benchmark/corpus）")
    parser.add_argument("--problem", action="append", help="只生成这道题，可重复；默认全部")
    parser.add_argument("--update-lock", action="store_true", help="用新数据更新 corpus.lock（必须同时给 --reason）")
    parser.add_argument("--reason", help="更新锁定文件的原因，会打印出来供提交说明引用")
    args = parser.parse_args(argv)
    if args.update_lock and not args.reason:
        parser.error("--update-lock 必须同时给 --reason，说明为什么数据变了")
    root = args.corpus.resolve()
    names = sorted(args.problem or [p.name for p in (root / "judge").iterdir() if p.is_dir()])
    try:
        for name in names:
            problem_dir = root / "judge" / name
            if not problem_dir.is_dir():
                raise GenerateError(f"没有题目 {name}")
            scratch = regenerate(problem_dir, read_seed(problem_dir))
            try:
                target = problem_dir / "testdata"
                if target.is_dir():
                    shutil.rmtree(target)
                shutil.copytree(scratch, target)
            finally:
                shutil.rmtree(scratch.parent, ignore_errors=True)
            print(f"✓ {name}：写入 {target}")
        # 重新读取：题目的 expect.json 要与新数据一致，否则这里就会报错。
        corpus = corpus_lib.load_corpus(root, require_lock=not args.update_lock)
        digests = {name: problem.digest for name, problem in corpus.problems.items()}
        if args.update_lock:
            (root / "corpus.lock").write_text(json.dumps(corpus_lib.lock_document(digests), indent=2) + "\n",
                                              encoding="utf-8")
            print(f"✓ 更新 corpus.lock，原因：{args.reason}")
        else:
            for line in corpus_lib.verify_lock(corpus):
                print(f"✗ 数据与锁定文件不一致：{line}", file=sys.stderr)
                return 1
    except (GenerateError, corpus_lib.CorpusError) as error:
        print(f"错误：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
