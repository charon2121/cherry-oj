import sys

block = b"x" * 4096
for _ in range(512):
    sys.stdout.buffer.write(block)
