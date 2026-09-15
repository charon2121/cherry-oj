// 对照空组 cgroup.kill 与 CLONE_INTO_CGROUP 的交互，仅在独占封顶测试单元运行。
package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
)

func main() {
	b, err := os.ReadFile("/proc/self/cgroup")
	must(err)
	rel := strings.TrimSpace(strings.SplitN(string(b), "::", 2)[1])
	if !strings.HasPrefix(filepath.Base(rel), "cherry-sandbox-test-") {
		panic("not test unit")
	}
	for _, kill := range []bool{false, true} {
		path := "/sys/fs/cgroup" + rel + fmt.Sprintf("/clone-test-%t", kill)
		must(os.Mkdir(path, 0700))
		if kill {
			must(os.WriteFile(path+"/cgroup.kill", []byte("1"), 0600))
		}
		fd, err := os.Open(path)
		must(err)
		cmd := exec.Command("/usr/bin/true")
		cmd.SysProcAttr = &syscall.SysProcAttr{UseCgroupFD: true, CgroupFD: int(fd.Fd()), Cloneflags: syscall.CLONE_NEWNS | syscall.CLONE_NEWPID | syscall.CLONE_NEWNET | syscall.CLONE_NEWIPC | syscall.CLONE_NEWUTS | syscall.CLONE_NEWCGROUP}
		err = cmd.Run()
		fmt.Printf("empty-group-kill=%t run=%v\n", kill, err)
		must(fd.Close())
		must(os.Remove(path))
	}
}
func must(err error) {
	if err != nil {
		panic(err)
	}
}
