#include <cstdlib>

// Touch physical pages; address-space reservation alone is not a memory test.
int main() {
    constexpr unsigned long bytes = 320UL << 20;
    volatile unsigned char* memory = static_cast<unsigned char*>(std::malloc(bytes));
    if (!memory) return 9;
    for (unsigned long i = 0; i < bytes; i += 4096) memory[i] = 1;
    std::free(const_cast<unsigned char*>(memory));
    return 10; // A functioning 256 MiB group limit must prevent completion.
}
