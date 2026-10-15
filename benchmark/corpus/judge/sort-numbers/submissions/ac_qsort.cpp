#include <cstdio>
#include <cstdlib>

static int compare(const void *a, const void *b) {
    long long x = *(const long long *)a, y = *(const long long *)b;
    return (x > y) - (x < y);
}

int main() {
    int n;
    if (scanf("%d", &n) != 1) return 2;
    long long *v = (long long *)malloc(sizeof(long long) * n);
    for (int i = 0; i < n; ++i) scanf("%lld", &v[i]);
    qsort(v, n, sizeof(long long), compare);
    for (int i = 0; i < n; ++i) printf("%lld%c", v[i], i + 1 < n ? ' ' : '\n');
}
