// 死循环，不睡眠：用 CPU 时间上限终止它。
int main() {
    volatile unsigned long long x = 0;
    for (;;) x = x + 1;
}
