#!/usr/bin/env python3
"""参考解 B：逐行逐 token 读取累加，用计数器判断有没有整数，与 A 的一次读完不同。"""
import sys

count = 0
total = 0
for line in sys.stdin:
    for token in line.split():
        total += int(token)
        count += 1
if count:
    sys.stdout.write(str(total) + "\n")
