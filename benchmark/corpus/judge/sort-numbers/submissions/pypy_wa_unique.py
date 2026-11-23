input()
numbers = list(map(int, input().split()))
print(*sorted(set(numbers)))
