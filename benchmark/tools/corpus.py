"""语料的读取、校验与指纹。

格式见 docs/protocols/benchmark.md §2、§3。读取是严格的：未知字段、重复的键、取值不合规一律拒绝，
而不是尽量读下去——预期自相矛盾的语料不应该跑出一份看似有意义的报告。
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
sys.dont_write_bytecode = True  # 不往仓库里已入库的 scripts/__pycache__ 写新文件
sys.path.insert(0, str(ROOT / "scripts"))
import testdata_pack  # noqa: E402  （仓库脚本：生成与核对 testdata.json）

PROBLEM_NAME = re.compile(r"[a-z0-9][a-z0-9-]{0,62}")
ITEM_NAME = re.compile(r"[a-z0-9][a-z0-9_]{0,62}")
VERDICTS = ("AC", "WA", "PE", "CE", "RE", "TLE", "MLE", "OLE", "SE")
TRIAL_VERDICTS = VERDICTS + ("RAN",)
# 从最严重到最轻（协议 §3.1）；RAN 只出现在试运行里。
SEVERITY = ("SE", "CE", "RE", "MLE", "OLE", "TLE", "WA", "PE", "RAN", "AC")
LANGUAGES = {".cpp": "cpp"}
ASSERTION_KEYS = ("equals", "startsWith", "contains")
LIMIT_KEYS = ("cpuNs", "memoryBytes", "clockNs")
REQUIRES_PATTERNS = (
    re.compile(r"backend:[a-z0-9-]+"),
    re.compile(r"strictWhitespace:(true|false)"),
    re.compile(r"entry:(judge|sandbox)"),
    re.compile(r"noLanguage:[a-z0-9]+"),
)


class CorpusError(Exception):
    """语料本身不合格（格式、预期自相矛盾、指纹对不上）。"""


def worst(verdicts: list[str]) -> str:
    """按严重度取最坏者；空列表没有意义，由调用方处理。"""
    return min(verdicts, key=SEVERITY.index)


# ---------------------------------------------------------------------------------------------------------------------
# 严格读取

def _no_duplicates(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    keys = [key for key, _ in pairs]
    duplicated = sorted({key for key in keys if keys.count(key) > 1})
    if duplicated:
        raise ValueError(f"重复的键：{', '.join(duplicated)}")
    return dict(pairs)


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=_no_duplicates)
    except (OSError, ValueError) as error:
        raise CorpusError(f"{path}：{error}") from error


def fields(value: Any, where: str, required: tuple[str, ...], optional: tuple[str, ...] = ()) -> dict[str, Any]:
    """校验对象的字段集合：必填的都有，没有表以外的字段。"""
    if not isinstance(value, dict):
        raise CorpusError(f"{where} 必须是对象")
    missing = [key for key in required if key not in value]
    unknown = [key for key in value if key not in required and key not in optional]
    if missing:
        raise CorpusError(f"{where} 缺少字段：{', '.join(missing)}")
    if unknown:
        raise CorpusError(f"{where} 有未知字段：{', '.join(unknown)}")
    return value


def positive_int(value: Any, where: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise CorpusError(f"{where} 必须是正整数")
    return value


# ---------------------------------------------------------------------------------------------------------------------
# 数据结构

@dataclass(frozen=True)
class Check:
    """对一段文本的断言：equals、startsWith、contains 之一。"""

    kind: str
    value: str

    def matches(self, text: str) -> bool:
        if self.kind == "equals":
            return text == self.value
        if self.kind == "startsWith":
            return text.startswith(self.value)
        return self.value in text

    def describe(self) -> str:
        return f"{self.kind} {self.value!r}"


def parse_check(value: Any, where: str) -> Check:
    obj = fields(value, where, (), ASSERTION_KEYS)
    if len(obj) != 1:
        raise CorpusError(f"{where} 必须恰好给出 {'、'.join(ASSERTION_KEYS)} 之一")
    (kind, text), = obj.items()
    if not isinstance(text, str):
        raise CorpusError(f"{where}.{kind} 必须是字符串")
    return Check(kind, text)


def parse_requires(value: Any, where: str) -> tuple[str, ...]:
    if not isinstance(value, list) or not all(isinstance(tag, str) for tag in value):
        raise CorpusError(f"{where} 必须是字符串数组")
    for tag in value:
        if not any(pattern.fullmatch(tag) for pattern in REQUIRES_PATTERNS):
            raise CorpusError(f"{where} 里的标签 {tag!r} 不认识")
    return tuple(value)


@dataclass(frozen=True)
class Limits:
    cpu_ns: int
    memory_bytes: int
    clock_ns: int

    def request(self, override: dict[str, int] | None = None) -> dict[str, int]:
        limits = {"cpuNs": self.cpu_ns, "memoryBytes": self.memory_bytes, "clockNs": self.clock_ns}
        limits.update(override or {})
        return limits


@dataclass(frozen=True)
class Submission:
    name: str
    path: Path
    language: str
    source: str
    verdict: str
    testcases: tuple[str, ...]
    message: Check | None
    requires: tuple[str, ...]
    probe: bool
    sandbox: Any  # sandbox 入口的运行说明，M3 才解释，这里原样保存


@dataclass(frozen=True)
class RequestCase:
    name: str
    source: str | None
    source_text: str | None
    test_data_location: str | None
    language_id: str | None
    limits: dict[str, int]
    tamper: dict[str, str] | None
    verdict: str
    message: Check | None
    body: Check | None
    requires: tuple[str, ...]


@dataclass(frozen=True)
class TrialTestcase:
    name: str
    input: str
    expected: str | None
    verdict: str
    output: Check | None
    stderr: Check | None


@dataclass(frozen=True)
class TrialCase:
    name: str
    source: str | None
    source_text: str | None
    limits: dict[str, int]
    verdict: str
    testcases: tuple[TrialTestcase, ...]
    requires: tuple[str, ...]


@dataclass(frozen=True)
class Problem:
    name: str
    directory: Path
    title: str
    seed: int
    limits: Limits
    requires: tuple[str, ...]
    digest: str
    testcase_names: tuple[str, ...]
    submissions: dict[str, Submission] = field(default_factory=dict)
    requests: dict[str, RequestCase] = field(default_factory=dict)
    trials: dict[str, TrialCase] = field(default_factory=dict)

    @property
    def testdata(self) -> Path:
        return self.directory / "testdata"


@dataclass(frozen=True)
class Corpus:
    root: Path
    problems: dict[str, Problem]
    lock: dict[str, Any]
    attack_samples: tuple[str, ...]

    def digests(self) -> dict[str, str]:
        return {name: problem.digest for name, problem in self.problems.items()}


# ---------------------------------------------------------------------------------------------------------------------
# 指纹与锁定文件

def corpus_digest(digests: dict[str, str]) -> str:
    """按题名升序，每题一行 `<digest>  <题名>\\n`，拼接后取 SHA-256（协议 §2.4）。"""
    text = "".join(f"{digests[name]}  {name}\n" for name in sorted(digests))
    return hashlib.sha256(text.encode()).hexdigest()


def lock_document(digests: dict[str, str]) -> dict[str, Any]:
    return {"schemaVersion": 1, "problems": dict(sorted(digests.items())), "corpusDigest": corpus_digest(digests)}


def read_lock(root: Path) -> dict[str, Any]:
    path = root / "corpus.lock"
    lock = fields(read_json(path), str(path), ("schemaVersion", "problems", "corpusDigest"))
    if lock["schemaVersion"] != 1:
        raise CorpusError(f"{path} 的 schemaVersion 只支持 1")
    return lock


def verify_lock(corpus: Corpus) -> list[str]:
    """锁定文件与语料实际指纹的差异，空列表表示一致（协议 §2.4）。"""
    differences = []
    locked, actual = corpus.lock["problems"], corpus.digests()
    for name in sorted(set(locked) | set(actual)):
        if name not in actual:
            differences.append(f"锁定文件里有题 {name}，语料里没有")
        elif name not in locked:
            differences.append(f"语料里的题 {name} 没有锁定")
        elif locked[name] != actual[name]:
            differences.append(f"题 {name} 的指纹变了：锁定 {locked[name][:12]}…，实际 {actual[name][:12]}…")
    if corpus.lock["corpusDigest"] != corpus_digest(locked):
        differences.append("corpus.lock 里的 corpusDigest 与它自己列出的题目对不上")
    return differences


# ---------------------------------------------------------------------------------------------------------------------
# 读取语料

def testdata_info(directory: Path) -> tuple[str, tuple[str, ...]]:
    """核对测试数据目录与 testdata.json 一致，返回指纹与测试点名字。"""
    path = directory / "testdata.json"
    if not path.is_file():
        raise CorpusError(f"{directory} 里没有 testdata.json")
    declared = read_json(path)
    try:
        actual = testdata_pack.build(directory)
    except (OSError, ValueError) as error:
        raise CorpusError(f"{directory}：{error}") from error
    if declared != actual:
        raise CorpusError(f"{path} 与目录里的数据文件不一致（数据被改过，或 testdata.json 过期）")
    return declared["digest"], tuple(entry["name"] for entry in declared["testcases"])


def load_corpus(root: Path, require_lock: bool = True) -> Corpus:
    """读取语料。`require_lock=False` 只用于首次生成锁定文件：没有 corpus.lock 时用空的锁定内容。"""
    root = root.resolve()
    if not (root / "judge").is_dir():
        raise CorpusError(f"{root} 不是语料根：没有 judge/ 目录")
    problems: dict[str, Problem] = {}
    for directory in sorted((root / "judge").iterdir()):
        if directory.is_dir():
            problems[directory.name] = load_problem(directory)
    if not problems:
        raise CorpusError(f"{root / 'judge'} 里没有题目")
    attack = root / "attack"
    samples = tuple(sorted(p.name for p in attack.iterdir() if p.is_dir())) if attack.is_dir() else ()
    lock = read_lock(root) if require_lock or (root / "corpus.lock").exists() else lock_document({})
    return Corpus(root, problems, lock, samples)


def load_problem(directory: Path) -> Problem:
    name = directory.name
    if not PROBLEM_NAME.fullmatch(name):
        raise CorpusError(f"题名 {name!r} 不符合 {PROBLEM_NAME.pattern}")
    meta_path = directory / "problem.json"
    meta = fields(read_json(meta_path), str(meta_path),
                  ("schemaVersion", "name", "title", "seed", "limits", "requires"))
    if meta["schemaVersion"] != 1:
        raise CorpusError(f"{meta_path} 的 schemaVersion 只支持 1")
    if meta["name"] != name:
        raise CorpusError(f"{meta_path} 的 name 必须与目录名 {name} 相同")
    if not isinstance(meta["title"], str) or not meta["title"]:
        raise CorpusError(f"{meta_path} 的 title 必须是非空字符串")
    if isinstance(meta["seed"], bool) or not isinstance(meta["seed"], int):
        raise CorpusError(f"{meta_path} 的 seed 必须是整数")
    limits = fields(meta["limits"], f"{meta_path} 的 limits", LIMIT_KEYS)
    digest, names = testdata_info(directory / "testdata")
    problem = Problem(
        name=name, directory=directory, title=meta["title"], seed=meta["seed"],
        limits=Limits(*(positive_int(limits[key], f"{meta_path} 的 limits.{key}") for key in LIMIT_KEYS)),
        requires=parse_requires(meta["requires"], f"{meta_path} 的 requires"),
        digest=digest, testcase_names=names)
    expect_path = directory / "expect.json"
    expect = fields(read_json(expect_path), str(expect_path),
                    ("schemaVersion", "submissions", "requests"), ("trials",))
    if expect["schemaVersion"] != 1:
        raise CorpusError(f"{expect_path} 的 schemaVersion 只支持 1")
    submissions = load_submissions(problem, expect["submissions"], expect_path)
    return Problem(**{
        **problem.__dict__,
        "submissions": submissions,
        "requests": load_requests(problem, submissions, expect["requests"], expect_path),
        "trials": load_trials(submissions, expect.get("trials", {}), expect_path),
    })


def load_submissions(problem: Problem, declared: Any, expect_path: Path) -> dict[str, Submission]:
    if not isinstance(declared, dict):
        raise CorpusError(f"{expect_path} 的 submissions 必须是对象")
    directory = problem.directory / "submissions"
    files = {path.stem: path for path in directory.iterdir() if path.is_file()} if directory.is_dir() else {}
    for stem, path in files.items():
        if path.suffix not in LANGUAGES:
            raise CorpusError(f"{path} 的扩展名不是支持的语言（{', '.join(LANGUAGES)}）")
    if set(files) != set(declared):
        only_files = sorted(set(files) - set(declared))
        only_expect = sorted(set(declared) - set(files))
        raise CorpusError(f"{expect_path}：submissions 与 submissions/ 下的文件必须一一对应"
                          f"（只在文件里：{only_files}；只在预期里：{only_expect}）")
    result = {}
    for name in sorted(declared):
        where = f"{expect_path} 的 submissions.{name}"
        if not ITEM_NAME.fullmatch(name):
            raise CorpusError(f"提交名 {name!r} 不符合 {ITEM_NAME.pattern}")
        item = fields(declared[name], where, ("verdict", "testcases"),
                      ("message", "sandbox", "requires", "probe"))
        verdict = item["verdict"]
        if verdict not in VERDICTS:
            raise CorpusError(f"{where}.verdict 必须是 {'、'.join(VERDICTS)} 之一")
        testcases = item["testcases"]
        if not isinstance(testcases, list) or not all(v in VERDICTS for v in testcases):
            raise CorpusError(f"{where}.testcases 必须是 verdict 数组")
        if verdict == "CE":
            if testcases:
                raise CorpusError(f"{where}：CE 没有逐测试点结果，testcases 必须为空数组")
        else:
            if len(testcases) != len(problem.testcase_names):
                raise CorpusError(f"{where}.testcases 有 {len(testcases)} 项，题目有 {len(problem.testcase_names)} 个测试点")
            if worst(testcases) != verdict:
                raise CorpusError(f"{where}：整体 {verdict} 与逐点结论按严重度取最坏者（{worst(testcases)}）不一致")
        if not isinstance(item.get("probe", False), bool):
            raise CorpusError(f"{where}.probe 必须是布尔值")
        path = files[name]
        result[name] = Submission(
            name=name, path=path, language=LANGUAGES[path.suffix], source=path.read_text(encoding="utf-8"),
            verdict=verdict, testcases=tuple(testcases),
            message=parse_check(item["message"], f"{where}.message") if "message" in item else None,
            requires=parse_requires(item.get("requires", []), f"{where}.requires"),
            probe=item.get("probe", False), sandbox=item.get("sandbox"))
    return result


def parse_limits_override(value: Any, where: str) -> dict[str, int]:
    obj = fields(value, where, (), LIMIT_KEYS)
    return {key: positive_int(number, f"{where}.{key}") for key, number in obj.items()}


def source_reference(item: dict[str, Any], submissions: dict[str, Submission], where: str) -> tuple[str | None, str | None]:
    source, text = item.get("source"), item.get("sourceText")
    if (source is None) == (text is None):
        raise CorpusError(f"{where} 必须恰好给出 source 与 sourceText 之一")
    if source is not None and source not in submissions:
        raise CorpusError(f"{where}.source {source!r} 不是本题的提交")
    if text is not None and not isinstance(text, str):
        raise CorpusError(f"{where}.sourceText 必须是字符串")
    return source, text


def load_requests(problem: Problem, submissions: dict[str, Submission], declared: Any,
                  expect_path: Path) -> dict[str, RequestCase]:
    if not isinstance(declared, dict):
        raise CorpusError(f"{expect_path} 的 requests 必须是对象")
    result = {}
    for name in sorted(declared):
        where = f"{expect_path} 的 requests.{name}"
        if not ITEM_NAME.fullmatch(name):
            raise CorpusError(f"请求级用例名 {name!r} 不符合 {ITEM_NAME.pattern}")
        item = fields(declared[name], where, ("verdict",),
                      ("source", "sourceText", "testDataLocation", "languageId", "limits", "tamper",
                       "message", "body", "requires"))
        if item["verdict"] not in VERDICTS:
            raise CorpusError(f"{where}.verdict 必须是 {'、'.join(VERDICTS)} 之一")
        source, text = source_reference(item, submissions, where)
        tamper = None
        if "tamper" in item:
            raw = fields(item["tamper"], f"{where}.tamper", ("file", "appendText"))
            if raw["file"] not in {f"{n}{suffix}" for n in problem.testcase_names for suffix in (".in", ".out")}:
                raise CorpusError(f"{where}.tamper.file {raw['file']!r} 不是本题测试数据里的文件")
            if not isinstance(raw["appendText"], str) or not raw["appendText"]:
                raise CorpusError(f"{where}.tamper.appendText 必须是非空字符串")
            tamper = dict(raw)
        for key in ("testDataLocation", "languageId"):
            if key in item and not isinstance(item[key], str):
                raise CorpusError(f"{where}.{key} 必须是字符串")
        result[name] = RequestCase(
            name=name, source=source, source_text=text, test_data_location=item.get("testDataLocation"),
            language_id=item.get("languageId"),
            limits=parse_limits_override(item.get("limits", {}), f"{where}.limits"), tamper=tamper,
            verdict=item["verdict"],
            message=parse_check(item["message"], f"{where}.message") if "message" in item else None,
            body=parse_check(item["body"], f"{where}.body") if "body" in item else None,
            requires=parse_requires(item.get("requires", []), f"{where}.requires"))
    return result


def load_trials(submissions: dict[str, Submission], declared: Any, expect_path: Path) -> dict[str, TrialCase]:
    if not isinstance(declared, dict):
        raise CorpusError(f"{expect_path} 的 trials 必须是对象")
    result = {}
    for name in sorted(declared):
        where = f"{expect_path} 的 trials.{name}"
        if not ITEM_NAME.fullmatch(name):
            raise CorpusError(f"试运行用例名 {name!r} 不符合 {ITEM_NAME.pattern}")
        item = fields(declared[name], where, ("verdict", "testcases"),
                      ("source", "sourceText", "limits", "requires"))
        if item["verdict"] not in TRIAL_VERDICTS:
            raise CorpusError(f"{where}.verdict 必须是 {'、'.join(TRIAL_VERDICTS)} 之一")
        source, text = source_reference(item, submissions, where)
        raw_cases = item["testcases"]
        if not isinstance(raw_cases, list) or not raw_cases:
            raise CorpusError(f"{where}.testcases 必须是非空数组")
        cases = []
        for index, raw in enumerate(raw_cases):
            at = f"{where}.testcases[{index}]"
            case = fields(raw, at, ("name", "input", "verdict"), ("expected", "output", "stderr"))
            if case["verdict"] not in TRIAL_VERDICTS:
                raise CorpusError(f"{at}.verdict 必须是 {'、'.join(TRIAL_VERDICTS)} 之一")
            for key in ("name", "input"):
                if not isinstance(case[key], str):
                    raise CorpusError(f"{at}.{key} 必须是字符串")
            if "expected" in case and not isinstance(case["expected"], str):
                raise CorpusError(f"{at}.expected 必须是字符串")
            cases.append(TrialTestcase(
                name=case["name"], input=case["input"], expected=case.get("expected"), verdict=case["verdict"],
                output=parse_check(case["output"], f"{at}.output") if "output" in case else None,
                stderr=parse_check(case["stderr"], f"{at}.stderr") if "stderr" in case else None))
        if worst([c.verdict for c in cases]) != item["verdict"]:
            raise CorpusError(f"{where}：整体 {item['verdict']} 与逐点结论按严重度取最坏者不一致")
        result[name] = TrialCase(
            name=name, source=source, source_text=text,
            limits=parse_limits_override(item.get("limits", {}), f"{where}.limits"),
            verdict=item["verdict"], testcases=tuple(cases),
            requires=parse_requires(item.get("requires", []), f"{where}.requires"))
    return result
