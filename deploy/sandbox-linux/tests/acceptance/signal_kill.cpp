#include <csignal>

// SIGKILL alone is not evidence of CPU exhaustion or OOM.
int main() { return std::raise(SIGKILL); }
