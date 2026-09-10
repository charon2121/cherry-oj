#include <cstdio>
#include <cstring>

int main() {
    char block[4096];
    std::memset(block, 'x', sizeof(block));
    for (int i = 0; i < 512; ++i) {
        if (std::fwrite(block, 1, sizeof(block), stdout) != sizeof(block)) return 11;
    }
}
