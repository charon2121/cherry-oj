input()
total = sum(map(int, input().split()))
print((total + (1 << 31)) % (1 << 32) - (1 << 31))
