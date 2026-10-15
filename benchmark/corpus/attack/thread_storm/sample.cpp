#include <cerrno>
#include <cstdio>
#include <cstring>
#include <pthread.h>
#include <unistd.h>

static void *hold(void *) {
    sleep(4);
    return nullptr;
}

// 不断创建睡眠的线程，最多 80 个：线程也计入同一个数量上限。
int main() {
    for (int n = 0; n < 80; ++n) {
        pthread_t thread;
        int error = pthread_create(&thread, nullptr, hold, nullptr);
        if (error) {
            std::printf("threads-denied %d %s\n", n, std::strerror(error));
            return 0;
        }
    }
    std::fprintf(stderr, "thread limit did not reject bounded expansion\n");
    return 3;
}
