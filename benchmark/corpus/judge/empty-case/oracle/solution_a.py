#!/usr/bin/env python3
"""参考解 A：读完全部输入，没有整数就不输出。"""
import sys

values = list(map(int, sys.stdin.read().split()))
if values:
    print(sum(values))
