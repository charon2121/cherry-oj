input()
numbers = list(map(int, input().split()))
for last in range(len(numbers) - 1, 0, -1):
    for i in range(last):
        if numbers[i] > numbers[i + 1]:
            numbers[i], numbers[i + 1] = numbers[i + 1], numbers[i]
print(*numbers)
