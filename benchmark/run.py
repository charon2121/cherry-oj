#!/usr/bin/env python3
"""benchmark 的入口脚本：对一个已经在运行的 judge 节点跑语料，与预期逐项比对，输出报告。

    python3 benchmark/run.py --entries judge --judge-url http://127.0.0.1:5051 \\
            --published-root /srv/cherry-benchmark --declare backend=linux strictWhitespace=false

脚本**不安装也不启动**被测节点：运行它的机器上必须已有 judge 服务（`judge` 入口）。`sandbox` 入口尚未实现。
退出码：0 全部符合预期；1 有失败；2 没有失败但有不稳定项；3 环境或配置错误。
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent / "tools"))
import corpus as corpus_lib  # noqa: E402
import publish  # noqa: E402
import report as report_lib  # noqa: E402
from judge_client import ClientError, JudgeClient  # noqa: E402
from judge_entry import JudgeRunner, parse_declared  # noqa: E402

HERE = Path(__file__).resolve().parent
ENTRIES = ("judge", "sandbox")


def git_sha() -> str | None:
    try:
        done = subprocess.run(["git", "rev-parse", "HEAD"], cwd=HERE, capture_output=True, text=True, timeout=10)
    except (OSError, subprocess.TimeoutExpired):
        return None
    return done.stdout.strip() or None if done.returncode == 0 else None


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--entries", default="judge", help="入口：judge 或 sandbox（目前只实现了 judge）")
    parser.add_argument("--judge-url", default="http://127.0.0.1:5051", help="judge 的地址")
    parser.add_argument("--published-root", type=Path,
                        help="测试数据发布位置（绝对路径），judge 的运行身份必须能一路进得去")
    parser.add_argument("--corpus", type=Path, default=HERE / "corpus", help="语料根")
    parser.add_argument("--select", help="只运行这些题，逗号分隔")
    parser.add_argument("--declare", nargs="*", default=[], metavar="KEY=VALUE",
                        help="运行者声明的节点配置：backend、strictWhitespace、revealExpected、languages（逗号分隔）")
    parser.add_argument("--repeat", type=int, default=3, help="时间、内存类提交重复运行的遍数（默认 3）")
    parser.add_argument("--report", type=Path, help="报告 JSON 的路径（默认 benchmark/reports/<时间>.json）")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        entries = tuple(dict.fromkeys(e for e in args.entries.split(",") if e))
        unknown = [e for e in entries if e not in ENTRIES]
        if not entries or unknown:
            raise ValueError(f"--entries 只能是 {'、'.join(ENTRIES)}，收到 {args.entries!r}")
        if "sandbox" in entries:
            raise ValueError("sandbox 入口尚未实现（里程碑 M3）")
        if args.repeat < 1:
            raise ValueError("--repeat 必须是正整数")
        if args.published_root is None:
            raise ValueError("judge 入口需要 --published-root")
        declared = parse_declared(args.declare, entries)
        corpus = corpus_lib.load_corpus(args.corpus)
        problems = list(corpus.problems.values())
        if args.select:
            wanted = [name for name in args.select.split(",") if name]
            missing = [name for name in wanted if name not in corpus.problems]
            if missing:
                raise ValueError(f"没有这些题：{', '.join(missing)}")
            problems = [corpus.problems[name] for name in wanted]
        differences = corpus_lib.verify_lock(corpus)
        if differences:
            raise corpus_lib.CorpusError("语料与 corpus.lock 不一致：" + "；".join(differences))
        client = JudgeClient(args.judge_url)
        version = client.version()
    except (ValueError, corpus_lib.CorpusError, ClientError) as error:
        print(f"环境或配置错误：{error}", file=sys.stderr)
        return report_lib.EXIT_ENVIRONMENT

    started = report_lib.now()
    run_id = started.replace("-", "").replace(":", "").lower()
    runner = JudgeRunner(client, args.published_root.resolve(), declared, args.repeat, run_id)
    try:
        results = [runner.run_problem(problem) for problem in problems]
    except (publish.PublishError, OSError) as error:
        print(f"环境或配置错误：{error}", file=sys.stderr)
        return report_lib.EXIT_ENVIRONMENT
    report = report_lib.build_report(
        started, report_lib.now(),
        report_lib.environment(client.url, version.get("version"), declared, git_sha(),
                               corpus_lib.corpus_digest(corpus.digests())),
        results, len(corpus.attack_samples))
    destination = args.report or HERE / "reports" / f"{started.replace(':', '')}-{urlsplit(client.url).hostname}.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(report_lib.render_text(report))
    print(f"\n报告：{destination}")
    return report_lib.exit_code(results)


if __name__ == "__main__":
    sys.exit(main())
