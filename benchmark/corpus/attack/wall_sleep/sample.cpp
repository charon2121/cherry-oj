#include <unistd.h>

// 睡眠，不占 CPU：只能由墙钟上限终止。
int main() { sleep(30); }
