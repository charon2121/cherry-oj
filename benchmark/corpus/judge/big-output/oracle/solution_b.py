#!/usr/bin/env python3
"""参考解 B：逐个计数输出，与 A 一次拼接不同。"""
import sys

n = int(input())
i = 0
while i < n:
    i += 1
    sys.stdout.write(str(i) + "\n")
