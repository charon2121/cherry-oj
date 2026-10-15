#!/usr/bin/env python3
"""参考解 A：内置排序。"""
n = int(input())
values = list(map(int, input().split()))
print(" ".join(map(str, sorted(values))))
