a, b = map(int, input().split())
if a == 10:
    raise ValueError("benchmark runtime error")
print(a + b)
