"""语料读取共用的基础：严格的 JSON 读取、字段校验、文本断言与 requires 标签。"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

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
