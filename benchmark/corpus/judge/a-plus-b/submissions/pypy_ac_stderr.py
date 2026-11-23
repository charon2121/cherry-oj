import sys

a, b = map(int, input().split())
print("用于 Benchmark 的日志", file=sys.stderr)
print(a + b)
