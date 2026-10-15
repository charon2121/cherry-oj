// 死循环，远超 CPU 限制。
int main() {
    volatile unsigned long long x = 0;
    for (;;) x = x + 1;
}
