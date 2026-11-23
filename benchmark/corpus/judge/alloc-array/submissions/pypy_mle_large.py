size_mib = 512
blocks = []
for _ in range(size_mib):
    block = bytearray(1 << 20)
    for offset in range(0, len(block), 4096):
        block[offset] = 1
    blocks.append(block)
print(len(blocks))
