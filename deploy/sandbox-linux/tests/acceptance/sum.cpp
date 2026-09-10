#include <iostream>

// Public A+B reference fixture; contains no hidden test input or answer.
int main() {
    long long a, b;
    if (!(std::cin >> a >> b)) return 2;
    std::cout << a + b << '\n';
}
