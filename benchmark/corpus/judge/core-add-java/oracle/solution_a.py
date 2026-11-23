#!/usr/bin/env python3
"""复用加法基准题的整数加法参考解。"""

from __future__ import annotations

from pathlib import Path
from runpy import run_path

run_path(str(Path(__file__).resolve().parents[2] / "a-plus-b" / "oracle" / "solution_a.py"), run_name="__main__")
