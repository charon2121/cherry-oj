// 解引用空指针（SIGSEGV）。
int main() {
    volatile int *p = nullptr;
    *p = 1;
}
