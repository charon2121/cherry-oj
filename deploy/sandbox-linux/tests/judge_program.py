"""经 judge 的 /judge 驱动执行层时使用的 C++ 测试程序与请求构造。

judge 的 trial 只接受源码，运行时不带参数，所以测试模式从 stdin 的第一行读取：
    <mode> [参数...]\n<其余输入>
同一份源码编译一次，一个 trial 里的多个测试点各自选择模式。
"""
import http.client
import json

SOURCE = r'''
#include <unistd.h>
#include <signal.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <sys/mount.h>
#include <sys/ptrace.h>
#include <fcntl.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <iostream>
#include <pthread.h>
static void* sleeper(void*) { sleep(4); return nullptr; }
static void status_fields() {
  FILE* f = fopen("/proc/self/status", "r");
  char line[256];
  while (f && fgets(line, sizeof line, f))
    if (!strncmp(line, "Uid:", 4) || !strncmp(line, "Seccomp:", 8) || !strncmp(line, "NoNewPrivs:", 11) ||
        !strncmp(line, "CapEff:", 7))
      fputs(line, stdout);
}
int main() {
  std::string line, m, arg1, arg2;
  std::getline(std::cin, line);
  {
    size_t a = line.find(' ');
    m = line.substr(0, a);
    if (a != std::string::npos) {
      size_t b = line.find(' ', a + 1);
      arg1 = line.substr(a + 1, b == std::string::npos ? std::string::npos : b - a - 1);
      if (b != std::string::npos) arg2 = line.substr(b + 1);
    }
  }
  if (m == "echo") { std::string rest; while (std::getline(std::cin, rest)) std::cout << rest << "\n"; return 0; }
  if (m == "cpu") { for (;;) {} }
  if (m == "tree") { fork(); for (;;) {} }
  if (m == "memory") { for (;;) { char* p = (char*)malloc(1 << 20); if (!p) return 5; memset(p, 1, 1 << 20); asm volatile("" ::"r"(p) : "memory"); } }
  if (m == "output") { char b[4096]; memset(b, 'x', sizeof b); for (;;) fwrite(b, 1, sizeof b, stdout); }
  if (m == "kill") { raise(SIGKILL); }
  if (m == "nonzero") { return 7; }
  if (m == "sleep") { sleep(2); return 0; }
  if (m == "background") { if (!fork()) { setsid(); sleep(4); } return 0; }
  if (m == "threads") { pthread_t t[80]; for (int n = 0; n < 80; n++) if (pthread_create(&t[n], nullptr, sleeper, nullptr)) { puts("denied"); return 0; } return 8; }
  if (m == "network") { socket(AF_INET, SOCK_STREAM, 0); return 8; }
  if (m == "mount") { mount("none", "/tmp", "tmpfs", 0, nullptr); return 8; }
  if (m == "ptrace") { ptrace(PTRACE_TRACEME, 0, 0, 0); return 8; }
  if (m == "hostfile") { puts(access("/etc/hostname", F_OK) == 0 ? "visible" : "hidden"); return 0; }
  if (m == "symlink") { symlink("/etc/passwd", "out"); return 0; }
  if (m == "magiclink") { symlink("/proc/self/fd/0", "out"); return 0; }
  if (m == "hardlink") { int f = open("first", O_CREAT | O_WRONLY, 0600); write(f, "x", 1); close(f); link("first", "out"); return 0; }
  if (m == "identity") { status_fields(); return 0; }
  if (m == "hold") { char* p = (char*)malloc(60 << 20); if (!p) return 5; memset(p, 1, 60 << 20); sleep(3); return 0; }
  if (m == "privilege") { if (setuid(0) == 0) { puts("escalated"); return 9; } puts("denied"); return 0; }
  if (m == "isolation") {
    // arg1：本次的私有标记；arg2：宿主上 judge 的 PID。留在工作区的文件只属于自己，宿主 PID 不可见。
    FILE* f = fopen(("own-" + arg1).c_str(), "w"); fputs(arg1.c_str(), f); fclose(f);
    sleep(1);
    int peers = 0;
    for (const char* other : {"own-left", "own-right"}) if (("own-" + arg1) != other && access(other, F_OK) == 0) peers++;
    bool host = access(("/proc/" + arg2).c_str(), F_OK) == 0;
    printf("private %s peers=%d host=%s\n", arg1.c_str(), peers, host ? "visible" : "hidden");
    return 0;
  }
  return 0;
}
'''

LIMITS = dict(cpuNs=1_000_000_000, memoryBytes=64 << 20, clockNs=5_000_000_000)


def request(cases, limits=None, language='cpp', source=SOURCE):
    return dict(submissionId='work061-kernel', problemId='work061-probe', problemVersionId='v1',
                testDataVersionId='unused', languageId=language, source=source, mode='trial',
                cases=cases, limits=dict(LIMITS, **(limits or {})))


def case(mode, expected=None, rest=''):
    value = dict(input=mode + '\n' + rest, name=mode.split()[0])
    if expected is not None:
        value['expected'] = expected
    return value


def begin(port, body, timeout=60):
    c = http.client.HTTPConnection('127.0.0.1', port, timeout=timeout)
    c.request('POST', '/judge', json.dumps(body), {'Content-Type': 'application/json'})
    return c


def finish(c, disconnected=False):
    try:
        response = c.getresponse()
        data = response.read(4 << 20)
        assert response.status == 200, (response.status, data[:512])
        return json.loads(data)
    except (OSError, http.client.HTTPException):
        if disconnected:
            return {'verdict': 'Disconnected'}
        raise
    finally:
        c.close()


def judge(port, cases, **kwargs):
    return finish(begin(port, request(cases, **kwargs)))
