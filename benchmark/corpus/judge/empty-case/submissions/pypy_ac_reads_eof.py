import sys

values = sys.stdin.read().split()
if values:
    print(sum(map(int, values)))
