#include <chrono>

// Run only in the hardened backend. The guard also bounds a broken limiter.
int main() {
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(8);
    volatile unsigned long long counter = 0;
    while (std::chrono::steady_clock::now() < deadline) {
        for (int i = 0; i < 10000; ++i) ++counter;
    }
    return 8; // A functioning 1 s CPU budget must terminate before this point.
}
