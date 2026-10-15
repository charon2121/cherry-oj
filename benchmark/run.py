#!/usr/bin/env python3
"""benchmark 的入口脚本：对一个已经在运行的 judge 节点跑语料，与预期逐项比对，输出报告。

    python3 benchmark/run.py --entries judge --judge-url http://127.0.0.1:5051 \\
            --published-root /srv/cherry-benchmark --declare backend=linux strictWhitespace=false languages=cpp
    sudo python3 benchmark/run.py --entries judge,sandbox ...      # 再加上直接调用执行器的 sandbox 入口

脚本**不安装也不启动**被测节点：运行它的机器上必须已有在运行的 judge 服务和执行器二进制。
`sandbox` 入口要在节点本机以 root 或执行器的服务身份运行，且运行期间 judge 不能有别的请求（脚本是节点上唯一的客户端）。
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
from executor import Executor, ExecutorError, load_config  # noqa: E402
from judge_client import ClientError, JudgeClient  # noqa: E402
from judge_entry import JudgeRunner, parse_declared  # noqa: E402
from node import DEFAULT_EXECUTOR, DEFAULT_EXECUTOR_CONFIG, NodeInspector  # noqa: E402
from sandbox_entry import SandboxRunner  # noqa: E402

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
    parser.add_argument("--entries", default="judge", help="入口，逗号分隔：judge、sandbox（默认 judge）")
    parser.add_argument("--judge-url", default="http://127.0.0.1:5051", help="judge 的地址")
    parser.add_argument("--published-root", type=Path,
                        help="测试数据发布位置（绝对路径），judge 的运行身份必须能一路进得去")
    parser.add_argument("--corpus", type=Path, default=HERE / "corpus", help="语料根")
    parser.add_argument("--executor", type=Path, default=DEFAULT_EXECUTOR, help="执行器二进制（sandbox 入口）")
    parser.add_argument("--executor-config", type=Path, default=DEFAULT_EXECUTOR_CONFIG,
                        help="执行器的受信配置（sandbox 入口，以及 judge 入口里攻击样本的回收检查）")
    parser.add_argument("--proc-root", type=Path, default=Path("/proc"), help=argparse.SUPPRESS)  # 测试用
    parser.add_argument("--select", help="只运行这些题或攻击样本，逗号分隔；默认全部")
    parser.add_argument("--declare", nargs="*", default=[], metavar="KEY=VALUE",
                        help="运行者声明的节点配置：backend、strictWhitespace、revealExpected、languages（逗号分隔）")
    parser.add_argument("--repeat", type=int, default=3, help="时间、内存类提交重复运行的遍数（默认 3）")
    parser.add_argument("--report", type=Path, help="报告 JSON 的路径（默认 benchmark/reports/<时间>.json）")
    return parser.parse_args(argv)


def select(corpus: corpus_lib.Corpus, wanted: str | None) -> tuple[list[corpus_lib.Problem], tuple]:
    """--select 同时接受题名和攻击样本名；不给就是全部。"""
    if not wanted:
        return list(corpus.problems.values()), corpus.attack_samples
    names = [name for name in wanted.split(",") if name]
    samples = {sample.name: sample for sample in corpus.attack_samples}
    missing = [name for name in names if name not in corpus.problems and name not in samples]
    if missing:
        raise ValueError(f"没有这些题或攻击样本：{', '.join(missing)}")
    return ([corpus.problems[name] for name in names if name in corpus.problems],
            tuple(samples[name] for name in names if name in samples))


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        entries = tuple(dict.fromkeys(e for e in args.entries.split(",") if e))
        unknown = [e for e in entries if e not in ENTRIES]
        if not entries or unknown:
            raise ValueError(f"--entries 只能是 {'、'.join(ENTRIES)}，收到 {args.entries!r}")
        if args.repeat < 1:
            raise ValueError("--repeat 必须是正整数")
        if "judge" in entries and args.published_root is None:
            raise ValueError("judge 入口需要 --published-root")
        declared = parse_declared(args.declare, entries)
        corpus = corpus_lib.load_corpus(args.corpus)
        problems, samples = select(corpus, args.select)
        differences = corpus_lib.verify_lock(corpus)
        if differences:
            raise corpus_lib.CorpusError("语料与 corpus.lock 不一致：" + "；".join(differences))
        client = version = None
        if "judge" in entries:
            client = JudgeClient(args.judge_url)
            version = client.version()
        executor, inspector = None, None
        if "sandbox" in entries:
            executor = Executor(args.executor, load_config(args.executor_config))
            inspector = NodeInspector(executor.config, args.proc_root)
        elif samples:
            try:  # judge 入口里的回收检查要读节点本机的配置；读不到就跳过攻击样本，而不是猜
                inspector = NodeInspector(load_config(args.executor_config), args.proc_root)
            except ExecutorError:
                inspector = None
    except (ValueError, corpus_lib.CorpusError, ClientError, ExecutorError) as error:
        print(f"环境或配置错误：{error}", file=sys.stderr)
        return report_lib.EXIT_ENVIRONMENT

    started = report_lib.now()
    run_id = started.replace("-", "").replace(":", "").lower()
    results, attack = [], []
    try:
        if client is not None:
            runner = JudgeRunner(client, args.published_root.resolve(), declared, args.repeat, run_id)
            results += [runner.run_problem(problem) for problem in problems]
            if corpus.attack_common is not None:
                attack += runner.run_attacks(corpus.attack_common, samples, inspector)
        if executor is not None:
            assert inspector is not None
            sandbox = SandboxRunner(executor, inspector, declared,
                                    corpus.attack_common.probe_source if corpus.attack_common else None, args.repeat)
            more, more_attack = sandbox.run(corpus, problems, True if samples else False)
            results += more
            attack += [a for a in more_attack if a.name in {s.name for s in samples}]
    except (publish.PublishError, ExecutorError, OSError) as error:
        print(f"环境或配置错误：{error}", file=sys.stderr)
        return report_lib.EXIT_ENVIRONMENT
    results, attack = report_lib.merge(results), report_lib.merge_attack(attack)
    endpoint = client.url if client is not None else "（未使用 judge 入口）"
    report = report_lib.build_report(
        started, report_lib.now(),
        report_lib.environment(endpoint, version.get("version") if version else None, declared, git_sha(),
                               corpus_lib.corpus_digest(corpus.digests())),
        results, len(samples), attack)
    host = urlsplit(client.url).hostname if client is not None else "node"
    destination = args.report or HERE / "reports" / f"{started.replace(':', '')}-{host}.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(report_lib.render_text(report))
    print(f"\n报告：{destination}")
    return report_lib.exit_code(results, attack)


if __name__ == "__main__":
    sys.exit(main())
