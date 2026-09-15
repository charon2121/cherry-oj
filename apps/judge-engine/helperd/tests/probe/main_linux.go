// 静态测试夹具，仅验证最小 Linux 链，不代表 C++ 工具链 rootfs。
package main

import (
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"syscall"
	"time"
)

func main() {
	if filepath.Base(os.Args[0]) == "true" {
		return
	}
	mode := "identity"
	if len(os.Args) > 1 {
		mode = os.Args[1]
	}
	switch mode {
	case "identity":
		status, err := os.ReadFile("/proc/self/status")
		if err != nil {
			panic(err)
		}
		fields := map[string]string{}
		for _, line := range strings.Split(string(status), "\n") {
			k, v, ok := strings.Cut(line, ":")
			if ok && (strings.HasPrefix(k, "Cap") || k == "Uid" || k == "Gid" || k == "NoNewPrivs" || k == "Seccomp" || k == "NSpid") {
				fields[k] = strings.TrimSpace(v)
			}
		}
		_, err = os.Stat("/etc/hostname")
		fields["hostFileAbsent"] = fmt.Sprint(os.IsNotExist(err))
		err = os.WriteFile("/usr/bin/escape", []byte("x"), 0600)
		fields["rootWriteDenied"] = fmt.Sprint(err != nil)
		fds, err := os.ReadDir("/proc/self/fd")
		if err != nil {
			panic(err)
		}
		var targets []string
		for _, fd := range fds {
			target, e := os.Readlink("/proc/self/fd/" + fd.Name())
			if e == nil {
				targets = append(targets, target)
			}
		}
		result := map[string]any{"status": fields, "fds": targets, "pid": os.Getpid(), "ppid": os.Getppid()}
		if err = json.NewEncoder(os.Stdout).Encode(result); err != nil {
			panic(err)
		}
	case "isolation":
		if len(os.Args) != 4 || (os.Args[2] != "left" && os.Args[2] != "right") {
			panic("invalid fixture arguments")
		}
		token := os.Args[2]
		other := "left"
		if token == "left" {
			other = "right"
		}
		input, err := os.ReadFile("own")
		if err != nil || string(input) != token {
			panic("input crossed executions")
		}
		if err := os.WriteFile("private-"+token, []byte(token), 0600); err != nil {
			panic(err)
		}
		time.Sleep(200 * time.Millisecond)
		if _, err := os.Stat("private-" + other); !os.IsNotExist(err) {
			panic("peer workspace visible")
		}
		pid, err := strconv.Atoi(os.Args[3])
		if err != nil || pid <= 100 {
			panic("invalid host fixture pid")
		}
		if err := syscall.Kill(pid, 0); err != syscall.ESRCH {
			panic(fmt.Sprintf("host process visible: %v", err))
		}
		fmt.Println("private", token)
	case "privilege":
		if err := syscall.Setuid(0); err == nil || os.Geteuid() == 0 {
			panic("privilege escalation")
		}
		fmt.Println("denied")
	case "sleep":
		time.Sleep(4 * time.Second)
	case "background":
		child := exec.Command("probe", "sleep")
		child.Env = os.Environ()
		child.SysProcAttr = &syscall.SysProcAttr{Setsid: true}
		child.Stdout = os.Stdout
		child.Stderr = os.Stderr
		if err := child.Start(); err != nil {
			panic(err)
		}
		fmt.Println("background-started", child.Process.Pid)
		// 故意不 Wait：测试主进程结束后的后代由 helper 整组回收。
	case "processes":
		var children []*exec.Cmd
		for i := 0; i < 80; i++ {
			child := exec.Command("probe", "sleep")
			child.Env = os.Environ()
			if err := child.Start(); err != nil {
				fmt.Println("spawn-denied", len(children), err)
				return
			}
			children = append(children, child)
		}
		panic("process limit did not reject bounded expansion")
	case "threads":
		ready := make(chan struct{})
		for i := 0; i < 80; i++ {
			go func() { runtime.LockOSThread(); ready <- struct{}{}; time.Sleep(4 * time.Second) }()
			<-ready
		}
		panic("thread limit did not reject bounded expansion")
	case "cpu":
		for {
		}
	case "memory":
		var chunks [][]byte
		for {
			b := make([]byte, 1<<20)
			for i := 0; i < len(b); i += 4096 {
				b[i] = 1
			}
			chunks = append(chunks, b)
			runtime.KeepAlive(chunks)
		}
	case "output":
		for {
			fmt.Print(strings.Repeat("x", 4096))
		}
	case "network":
		_, _, err := syscall.RawSyscall(syscall.SYS_SOCKET, syscall.AF_INET, syscall.SOCK_STREAM, 0)
		fmt.Println(err)
	default:
		panic("unknown test")
	}
}
