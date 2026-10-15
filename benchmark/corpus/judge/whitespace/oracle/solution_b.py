#!/usr/bin/env python3
"""参考解 B：用位运算做加法（异或求和、与加移位求进位），与 A 的算术加法不同。"""

MASK = (1 << 64) - 1


def add(x: int, y: int) -> int:
    x, y = x & MASK, y & MASK
    while y:
        x, y = (x ^ y) & MASK, ((x & y) << 1) & MASK
    return x - (1 << 64) if x >> 63 else x


a, b = map(int, input().split())
print(add(a, b))
