#!/usr/bin/env python3
"""校验进入 Git 的 Markdown 文档入口和本地链接。"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
LINK_PATTERN = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
ACTIVE_ENTRYPOINTS = (
    ROOT / "AGENTS.md",
    ROOT / "CLAUDE.md",
    ROOT / "README.md",
    ROOT / "docs" / "README.md",
    ROOT / "development" / "README.md",
)


def markdown_paths() -> list[Path]:
    paths = list(ACTIVE_ENTRYPOINTS)
    paths.extend((ROOT / "docs").rglob("*.md"))
    paths.extend((ROOT / "development").rglob("*.md"))
    return sorted(set(paths))


def local_target(path: Path, raw_target: str) -> Path | None:
    target = raw_target.strip().strip("<>")
    if not target or target.startswith(("#", "http://", "https://", "mailto:")):
        return None
    target = unquote(target.split("#", 1)[0])
    return (path.parent / target).resolve()


def tracked_paths() -> set[Path]:
    output = subprocess.check_output(("git", "ls-files", "-z"), cwd=ROOT)
    return {
        (ROOT / raw_path.decode("utf-8")).resolve()
        for raw_path in output.split(b"\0")
        if raw_path
    }


def repository_target_is_tracked(target: Path, repository_paths: set[Path]) -> bool:
    if target in repository_paths:
        return True
    return any(target in path.parents for path in repository_paths)


def main() -> int:
    errors: list[str] = []
    repository_paths = tracked_paths()
    for path in ACTIVE_ENTRYPOINTS:
        if not path.is_file():
            errors.append(f"缺少文档入口：{path.relative_to(ROOT)}")

    for path in markdown_paths():
        if not path.is_file():
            continue
        content = path.read_text(encoding="utf-8")
        for line_number, line in enumerate(content.splitlines(), start=1):
            for match in LINK_PATTERN.finditer(line):
                target = local_target(path, match.group(1))
                if target is not None and not target.exists():
                    errors.append(
                        f"{path.relative_to(ROOT)}:{line_number}: 本地链接不存在：{match.group(1)}"
                    )
                elif target is not None and not repository_target_is_tracked(
                    target, repository_paths
                ):
                    errors.append(
                        f"{path.relative_to(ROOT)}:{line_number}: "
                        f"本地链接目标未进入 Git：{match.group(1)}"
                    )

    ignore_lines = {
        line.strip()
        for line in (ROOT / ".gitignore").read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    }
    for forbidden in ("docs/", "development/"):
        if forbidden in ignore_lines:
            errors.append(f".gitignore 不能忽略全局或开发文档目录：{forbidden}")

    for error in errors:
        print(f"error: {error}", file=sys.stderr)
    if errors:
        print(f"发现 {len(errors)} 个文档入口或链接错误", file=sys.stderr)
        return 1
    print(f"✓ {len(markdown_paths())} 份 Markdown 文档入口和本地链接有效")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
