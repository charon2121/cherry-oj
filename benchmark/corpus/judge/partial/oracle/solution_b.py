#!/usr/bin/env python3
"""参考解 B：用 divmod 按十进制位拆开再逐位相加，与 A 的直接加法不同。"""


def digits(value: int) -> list[int]:
    out = []
    value = abs(value)
    while value:
        value, digit = divmod(value, 10)
        out.append(digit)
    return out or [0]


def number(parts: list[int]) -> int:
    result = 0
    for digit in reversed(parts):
        result = result * 10 + digit
    return result


a, b = map(int, input().split())
sign_a, sign_b = (-1 if a < 0 else 1), (-1 if b < 0 else 1)
if sign_a == sign_b:
    columns = [x + y for x, y in zip(digits(a) + [0] * 20, digits(b) + [0] * 20)]
    total, place = 0, 1
    for column in columns:
        total += column * place
        place *= 10
    print(sign_a * total)
else:
    big, small = (a, b) if abs(a) >= abs(b) else (b, a)
    print((1 if big > 0 else -1) * (number(digits(big)) - number(digits(small))))
