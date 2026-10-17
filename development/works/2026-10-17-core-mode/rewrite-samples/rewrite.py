"""Language-agnostic line rewriter prototype: subtract the prefix length from every location in the user file.

Location shapes seen across 13 toolchains:
  name:LINE          gcc, clang, go, rust, java, kotlin, swift, js, ruby, php
  name(LINE,COL)     C#, TypeScript
  name", line LINE   Python
  name on line LINE  PHP
plus source excerpts with a "  LINE |" gutter (gcc, rust, swift) that belong to the last location printed.
Any directory before the user file name is dropped, so sandbox paths never reach users.
"""
import re
import sys


def rewrite(text: str, name: str, offset: int) -> str:
    n = re.escape(name)
    path = r'(?:[^\s:"\'()]*/)?'
    shapes = [
        re.compile(rf'{path}({n}:)(\d+)'),
        re.compile(rf'{path}({n}\()(\d+)(?=,)'),
        re.compile(rf'{path}({n}", line )(\d+)'),
        re.compile(rf'{path}({n} on line )(\d+)'),
    ]
    any_location = re.compile(r'[\w.\-/]+\.\w+(?::\d+|\(\d+,|", line \d+| on line \d+)')
    gutter = re.compile(r'^(\s*)(\d+)( \|)')

    def shift(match: re.Match) -> str:
        line = int(match.group(2)) - offset
        shown = str(line) if line >= 1 else f"<prelude {int(match.group(2))}>"
        return match.group(1) + shown

    out, in_user = [], False
    for raw in text.split("\n"):
        line = raw
        hit = False
        for shape in shapes:
            line, count = shape.subn(shift, line)
            hit = hit or count > 0
        if hit:
            in_user = True
        elif any_location.search(raw):
            in_user = False
        elif in_user:
            line = gutter.sub(lambda m: m.group(1) + str(int(m.group(2)) - offset).rjust(len(m.group(2))) + m.group(3), line)
        out.append(line)
    return "\n".join(out)


if __name__ == "__main__":
    print(rewrite(sys.stdin.read(), sys.argv[1], int(sys.argv[2])))
