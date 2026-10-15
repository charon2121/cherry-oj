#!/usr/bin/env python3
"""参考解 B：手写堆排序（原地），与 A 的内置排序不同。"""


def sift_down(a: list[int], start: int, end: int) -> None:
    root = start
    while True:
        child = 2 * root + 1
        if child >= end:
            return
        if child + 1 < end and a[child + 1] > a[child]:
            child += 1
        if a[root] >= a[child]:
            return
        a[root], a[child] = a[child], a[root]
        root = child


n = int(input())
values = list(map(int, input().split()))
for start in range(n // 2 - 1, -1, -1):
    sift_down(values, start, n)
for end in range(n - 1, 0, -1):
    values[0], values[end] = values[end], values[0]
    sift_down(values, 0, end)
print(" ".join(map(str, values)))
