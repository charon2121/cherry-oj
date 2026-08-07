package container

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"syscall"
	"time"
)

type hostContainer struct {
	workDir string
}

type hostProcess struct {
	cmd *exec.Cmd
}

func NewHost() (*hostContainer, error) {
	dir, err := os.MkdirTemp("", "cherry-oj-*")
	if err != nil {
		return nil, err
	}
	return &hostContainer{
		workDir: dir,
	}, nil
}

func (p *hostProcess) Wait(ctx context.Context) (Usage, error) {

	// ctx 用不到
	err := p.cmd.Wait()
	var ee *exec.ExitError

	// 异常退出
	if err != nil && !errors.As(err, &ee) {
		return Usage{}, err
	}

	ps := p.cmd.ProcessState

	u := Usage{ExitCode: ps.ExitCode()}

	if ws, ok := ps.Sys().(syscall.WaitStatus); ok && ws.Signaled() {
		u.Signal = int(ws.Signal())
	}

	u.CPUNs = ps.UserTime().Nanoseconds() + ps.SystemTime().Nanoseconds()

	if ru, ok := ps.SysUsage().(*syscall.Rusage); ok {
		u.MemoryBytes = maxrssBytes(ru.Maxrss)
	}

	return u, nil
}

func (c *hostContainer) Start(ctx context.Context, s Spec) (Process, error) {

	if len(s.Command) == 0 {
		return nil, fmt.Errorf("container: empty command")
	}

	name := s.Command[0]

	if !strings.Contains(name, "/") {
		cand := filepath.Join(c.workDir, name) // 拼接工作目录
		if file, err := os.Stat(cand); err == nil && !file.IsDir() {
			name = cand
		}
	}

	cmd := exec.CommandContext(ctx, name, s.Command[1:]...)

	cmd.Dir = c.workDir
	cmd.Env = s.Env
	cmd.Stdin = s.Stdin
	cmd.Stdout = s.Stdout
	cmd.Stderr = s.Stderr

	// 设置父进程组
	cmd.SysProcAttr = &syscall.SysProcAttr{
		Setpgid: true,
	}

	// 当 pid 参数为负数时，向一个进程组发送信号，而不是单个进程。
	cmd.Cancel = func() error {
		return syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL)
	}

	// 解决子进程退出后，Wait 无限等待的问题
	cmd.WaitDelay = 2 * time.Second

	if err := cmd.Start(); err != nil {
		return nil, err
	}

	return &hostProcess{cmd: cmd}, nil
}

// 往容器中放文件
func (c *hostContainer) PutFile(name string, r io.Reader, mode os.FileMode) error {
	full, err := c.resolve(name)
	if err != nil {
		return err
	}

	if err := os.MkdirAll(filepath.Dir(full), 0o755); err != nil {
		return err
	}

	f, err := os.OpenFile(full, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
	if err != nil {
		return err
	}

	if _, err := io.Copy(f, r); err != nil {
		f.Close()
		return err
	}

	return f.Close()
}

func (c *hostContainer) GetFile(name string) (io.ReadCloser, error) {
	full, err := c.resolve(name)
	if err != nil {
		return nil, err
	}
	return os.Open(full)
}

func (c *hostContainer) Reset() error {
	entries, err := os.ReadDir(c.workDir)
	if err != nil {
		return err
	}

	for _, e := range entries {
		if err := os.RemoveAll(filepath.Join(c.workDir, e.Name())); err != nil {
			return err
		}
	}

	return nil
}

func (c *hostContainer) Close() error {
	return os.RemoveAll(c.workDir)
}

func (c *hostContainer) resolve(name string) (string, error) {
	// 归一化文件名，避免出现文件逃逸的情况
	clean := filepath.Clean("/" + name)
	full := filepath.Join(c.workDir, clean)

	// 确保 full 在 workdir 下
	rel, err := filepath.Rel(c.workDir, full)

	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return "", fmt.Errorf("path escapes workdir: %q", name)
	}
	return full, nil
}

func maxrssBytes(maxrss int64) int64 {
	if runtime.GOOS == "linux" {
		return maxrss * 1024
	}
	return maxrss
}
