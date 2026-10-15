#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <unistd.h>

static std::string status_value(const char *key) {
    FILE *file = std::fopen("/proc/self/status", "r");
    char line[512];
    std::string value;
    std::size_t length = std::strlen(key);
    while (file && std::fgets(line, sizeof line, file))
        if (!std::strncmp(line, key, length) && line[length] == ':') {
            value = line + length + 1;
            while (!value.empty() && (value[0] == ' ' || value[0] == '\t')) value.erase(0, 1);
            while (!value.empty() && (value.back() == '\n' || value.back() == ' ')) value.pop_back();
        }
    if (file) std::fclose(file);
    return value;
}

// 自检用户程序眼里的世界，全部符合就输出 identity-ok，否则输出第一处问题。
int main() {
    // 先数继承来的 fd：后面的 open 会产生新的 fd。
    int extra = 0;
    for (int fd = 3; fd < 256; ++fd) extra += fcntl(fd, F_GETFD) >= 0;
    const char *problem = nullptr;
    if (getuid() == 0 || getgid() == 0 || geteuid() != getuid() || getegid() != getgid()) problem = "uid/gid is privileged";
    for (const char *key : {"CapInh", "CapPrm", "CapEff", "CapBnd", "CapAmb"})
        if (!problem && status_value(key).find_first_not_of('0') != std::string::npos) problem = key;
    if (!problem && status_value("NoNewPrivs") != "1") problem = "NoNewPrivs";
    if (!problem && status_value("Seccomp") != "2") problem = "Seccomp";
    if (!problem && !(access("/etc/hostname", F_OK) != 0 && errno == ENOENT)) problem = "host file visible";
    if (!problem && open("/usr/bin/escape", O_CREAT | O_WRONLY, 0600) >= 0) problem = "root filesystem writable";
    char cwd[256];
    if (!problem && (!getcwd(cwd, sizeof cwd) || std::strcmp(cwd, "/work") != 0)) problem = "cwd is not /work";
    if (!problem && open("/tmp/probe", O_CREAT | O_WRONLY, 0600) < 0) problem = "/tmp not writable";
    if (!problem && getppid() != 1) problem = "ppid is not 1";
    if (!problem && extra != 0) problem = "inherited file descriptors";
    if (!problem && access("/.oldroot/etc", F_OK) == 0) problem = "host root visible";
    if (problem) {
        std::printf("identity-bad: %s\n", problem);
        return 1;
    }
    std::puts("identity-ok");
    return 0;
}
