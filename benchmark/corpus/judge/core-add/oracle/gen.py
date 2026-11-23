#!/usr/bin/env python3
"""复用加法基准题的固定输入生成器。"""

from __future__ import annotations

from pathlib import Path
from runpy import run_path

run_path(str(Path(__file__).resolve().parents[2] / "a-plus-b" / "oracle" / "gen.py"), run_name="__main__")
