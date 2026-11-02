#!/usr/bin/env python3
"""参考解 B：与 A 算法不同——把数字当十进制字符串，按位竖式加减，不使用整数加法。"""


def parse(token: str) -> tuple[int, list[int]]:
    sign = -1 if token.startswith("-") else 1
    digits = [int(c) for c in token.lstrip("+-")]
    return sign, digits[::-1]  # 低位在前


def compare(x: list[int], y: list[int]) -> int:
    """比较两个非负数的大小（低位在前）。"""
    x, y = trim(x), trim(y)
    if len(x) != len(y):
        return 1 if len(x) > len(y) else -1
    for p, q in zip(reversed(x), reversed(y)):
        if p != q:
            return 1 if p > q else -1
    return 0


def trim(x: list[int]) -> list[int]:
    x = list(x)
    while len(x) > 1 and x[-1] == 0:
        x.pop()
    return x


def add(x: list[int], y: list[int]) -> list[int]:
    out, carry = [], 0
    for i in range(max(len(x), len(y))):
        total = (x[i] if i < len(x) else 0) + (y[i] if i < len(y) else 0) + carry
        out.append(total % 10)
        carry = total // 10
    if carry:
        out.append(carry)
    return trim(out)


def sub(x: list[int], y: list[int]) -> list[int]:
    """x - y，要求 x >= y。"""
    out, borrow = [], 0
    for i in range(len(x)):
        value = x[i] - borrow - (y[i] if i < len(y) else 0)
        borrow = 1 if value < 0 else 0
        out.append(value + 10 if value < 0 else value)
    return trim(out)


def main() -> None:
    (sa, da), (sb, db) = (parse(t) for t in input().split())
    if sa == sb:
        sign, mag = sa, add(da, db)
    elif compare(da, db) >= 0:
        sign, mag = sa, sub(da, db)
    else:
        sign, mag = sb, sub(db, da)
    text = "".join(str(d) for d in reversed(mag))
    print(("-" if sign < 0 and text != "0" else "") + text)


main()
