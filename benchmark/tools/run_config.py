"""运行者声明的节点配置及共用运行默认值。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

DEFAULT_REPEATS = 3


@dataclass(frozen=True)
class Declared:
    """运行者声明的节点配置（协议 §4）。没有声明的项就是未知，依赖它的项会被跳过，而不是去猜。"""

    backend: str | None = None
    strict_whitespace: bool | None = None
    reveal_expected: bool = False
    languages: tuple[str, ...] | None = None
    entries: tuple[str, ...] = ("judge",)

    def to_report(self) -> dict[str, Any]:
        return {"backend": self.backend, "strictWhitespace": self.strict_whitespace,
                "revealExpected": self.reveal_expected,
                "languages": list(self.languages) if self.languages is not None else None,
                "entries": list(self.entries)}

    def unmet(self, tags: tuple[str, ...]) -> str | None:
        """第一个不满足的标签的原因；全部满足返回 None。"""
        for tag in tags:
            kind, _, value = tag.partition(":")
            if kind == "backend":
                if self.backend != value:
                    return f"需要 {tag}，运行者声明的 backend 是 {self.backend!r}"
            elif kind == "strictWhitespace":
                if self.strict_whitespace is None or self.strict_whitespace != (value == "true"):
                    return f"需要 {tag}，运行者声明的 strictWhitespace 是 {self.strict_whitespace!r}"
            elif kind == "entry":
                if value not in self.entries:
                    return f"需要 {tag}，本次运行的入口是 {', '.join(self.entries)}"
            elif kind == "noLanguage":
                if self.languages is None:
                    return f"需要 {tag}，但运行者没有声明 languages"
                if value in self.languages:
                    return f"需要 {tag}，但节点声明有 {value}"
        return None


def parse_declared(pairs: list[str], entries: tuple[str, ...]) -> Declared:
    values: dict[str, str] = {}
    for pair in pairs:
        key, separator, value = pair.partition("=")
        if not separator or not key:
            raise ValueError(f"--declare 的值必须是 key=value，收到 {pair!r}")
        values[key] = value

    def boolean(key: str) -> bool | None:
        if key not in values:
            return None
        if values[key] not in ("true", "false"):
            raise ValueError(f"--declare {key} 必须是 true 或 false")
        return values[key] == "true"

    unknown = set(values) - {"backend", "strictWhitespace", "revealExpected", "languages"}
    if unknown:
        raise ValueError(f"--declare 不认识 {', '.join(sorted(unknown))}")
    return Declared(
        backend=values.get("backend"), strict_whitespace=boolean("strictWhitespace"),
        reveal_expected=bool(boolean("revealExpected")),
        languages=tuple(v for v in values["languages"].split(",") if v) if "languages" in values else None,
        entries=entries)
