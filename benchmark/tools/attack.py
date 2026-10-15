"""攻击样本与共用内容的读取与校验（协议 §5）。"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import corpus as lib
from corpus_base import CorpusError, fields, parse_requires, positive_int, read_json
from specs import RunSpec, parse_run_specs


@dataclass(frozen=True)
class AttackCommon:
    """语料根下的 attack-common/：攻击样本共用的测试数据，和干净探针。"""

    testdata: Path
    digest: str
    testcase_names: tuple[str, ...]
    probe_source: str


@dataclass(frozen=True)
class AttackSample:
    name: str
    directory: Path
    source: str
    language: str
    limits: lib.Limits
    judge: tuple[str, str] | None  # ("verdict", "TLE") 或 ("verdictNot", "AC")
    sandbox: tuple[RunSpec, ...]
    repeat_judge: int
    repeat_sandbox: int | None  # None 表示按默认（时间、内存类重复 3 遍，其余 1 遍）
    requires: tuple[str, ...]
    notes: str


def load_common(root: Path) -> AttackCommon:
    directory = root / "attack-common"
    probe = directory / "probe.cpp"
    if not probe.is_file():
        raise CorpusError(f"{directory} 里没有 probe.cpp")
    digest, names = lib.testdata_info(directory / "testdata")
    return AttackCommon(directory / "testdata", digest, names, probe.read_text(encoding="utf-8"))


def load_attack(root: Path) -> tuple[AttackCommon | None, tuple[AttackSample, ...]]:
    attack = root / "attack"
    directories = sorted(p for p in attack.iterdir() if p.is_dir()) if attack.is_dir() else []
    if not directories and not (root / "attack-common").exists():
        return None, ()
    common = load_common(root)
    return common, tuple(load_sample(directory, root) for directory in directories)


def load_sample(directory: Path, root: Path) -> AttackSample:
    name = directory.name
    if not lib.ITEM_NAME.fullmatch(name):
        raise CorpusError(f"攻击样本名 {name!r} 不符合 {lib.ITEM_NAME.pattern}")
    path = directory / "manifest.json"
    manifest = fields(read_json(path), str(path), ("schemaVersion", "name", "language", "limits", "requires"),
                      ("source", "judge", "sandbox", "repeat", "notes"))
    if manifest["schemaVersion"] != 1:
        raise CorpusError(f"{path} 的 schemaVersion 只支持 1")
    if manifest["name"] != name:
        raise CorpusError(f"{path} 的 name 必须与目录名 {name} 相同")
    if manifest["language"] not in lib.LANGUAGES.values():
        raise CorpusError(f"{path} 的 language 必须是 {', '.join(lib.LANGUAGES.values())}")
    limits = fields(manifest["limits"], f"{path} 的 limits", lib.LIMIT_KEYS)
    source_path = (directory / manifest.get("source", "sample.cpp")).resolve()
    if root.resolve() not in source_path.parents or not source_path.is_file():
        raise CorpusError(f"{path} 的源码 {manifest.get('source', 'sample.cpp')!r} 不存在，或不在语料根之内")
    if source_path.suffix not in lib.LANGUAGES:
        raise CorpusError(f"{source_path} 的扩展名不是支持的语言")
    judge = None
    if "judge" in manifest:
        raw = fields(manifest["judge"], f"{path} 的 judge", (), ("verdict", "verdictNot"))
        if len(raw) != 1:
            raise CorpusError(f"{path} 的 judge 必须恰好给出 verdict 与 verdictNot 之一")
        (kind, value), = raw.items()
        if value not in lib.VERDICTS:
            raise CorpusError(f"{path} 的 judge.{kind} 必须是 {'、'.join(lib.VERDICTS)} 之一")
        judge = (kind, value)
    sandbox = parse_run_specs(manifest["sandbox"], f"{path} 的 sandbox", None) if "sandbox" in manifest else ()
    if judge is None and not sandbox:
        raise CorpusError(f"{path}：judge 与 sandbox 至少要有一个")
    repeat = fields(manifest.get("repeat", {}), f"{path} 的 repeat", (), ("judge", "sandbox"))
    return AttackSample(
        name=name, directory=directory, source=source_path.read_text(encoding="utf-8"),
        language=manifest["language"],
        limits=lib.Limits(*(positive_int(limits[key], f"{path} 的 limits.{key}") for key in lib.LIMIT_KEYS)),
        judge=judge, sandbox=sandbox,
        repeat_judge=positive_int(repeat["judge"], f"{path} 的 repeat.judge") if "judge" in repeat else 1,
        repeat_sandbox=positive_int(repeat["sandbox"], f"{path} 的 repeat.sandbox") if "sandbox" in repeat else None,
        requires=parse_requires(manifest["requires"], f"{path} 的 requires"),
        notes=str(manifest.get("notes", "")))
