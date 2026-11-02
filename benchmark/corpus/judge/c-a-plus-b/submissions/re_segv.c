#include <stdio.h>

int main(void) {
    volatile int *nowhere = NULL;
    *nowhere = 1;
    return 0;
}
