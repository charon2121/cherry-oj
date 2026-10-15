#!/usr/bin/env python3
"""参考解 B：把 k 转成二进制再还原，与 A 的直接输出不同。"""
bits = bin(int(input()))[2:]
value = 0
for bit in bits:
    value = value * 2 + int(bit)
print(value)
