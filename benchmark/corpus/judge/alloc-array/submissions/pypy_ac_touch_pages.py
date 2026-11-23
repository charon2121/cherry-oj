size_mib = int(input())
written = 0
for _ in range(size_mib):
    block = bytearray(1 << 20)
    for offset in range(0, len(block), 4096):
        block[offset] = 1
    written += block[0]
print(written)
