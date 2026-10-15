#!/usr/bin/env python3
"""参考解 B：分治求和（把序列对半分别求和再相加），与 A 的顺序累加不同。"""


def total(values: list[int]) -> int:
    if len(values) == 1:
        return values[0]
    middle = len(values) // 2
    return total(values[:middle]) + total(values[middle:])


n = int(input())
print(total(list(map(int, input().split()))))
