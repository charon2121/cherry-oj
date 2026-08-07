package container

import (
	"bytes"
	"context"
	"io"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"testing"
	"time"
)

// 确保 hostContainer 实现了 Container 接口
var _ Container = (*hostContainer)(nil)

// newBox 建一个工作间，并登记好清理。
// Close 只在这里登记一次 —— 各测试不要再自己 t.Cleanup(Close)。
func newBox(t *testing.T) *hostContainer {
	t.Helper()
	c, err := NewHost()
	if err != nil {
		t.Fatalf("NewHost: %v", err)
	}
	t.Cleanup(func() { c.Close() })
	return c
}

// PutFile <-> GetFile 往返，并验证文件真实落在 workDir 下
func TestPutGetRoundTrip(t *testing.T) {
	c := newBox(t)

	const name, body = "main.cpp", "int main() { return 0; }"

	if err := c.PutFile(name, strings.NewReader(body), 0o644); err != nil {
		t.Fatal(err)
	}

	// 文件必须真的在 workDir 下（而不是只测“读回来一致”）
	if _, err := os.Stat(filepath.Join(c.workDir, name)); err != nil {
		t.Fatalf("file not under workdir: %v", err)
	}

	rc, err := c.GetFile(name)
	if err != nil {
		t.Fatalf("GetFile: %v", err)
	}
	defer rc.Close()

	got, err := io.ReadAll(rc)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != body {
		t.Errorf("round trip = %q want %q", got, body)
	}
}

// 超时后子进程要被杀掉：取消 ctx → 很快返回 + Signal == SIGKILL
func TestStartTimeoutKills(t *testing.T) {
	c := newBox(t)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel() // 保险 + 消 go vet 的 lostcancel 警告

	p, err := c.Start(ctx, Spec{Command: []string{"/bin/sh", "-c", "sleep 100"}})
	if err != nil {
		t.Fatalf("Start: %v", err)
	}

	// 100ms 后取消，触发对进程组的 SIGKILL
	time.AfterFunc(100*time.Millisecond, cancel)

	done := make(chan Usage, 1)
	go func() { u, _ := p.Wait(context.Background()); done <- u }()

	select {
	case u := <-done:
		if u.Signal != int(syscall.SIGKILL) {
			t.Errorf("signal=%d want SIGKILL", u.Signal)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("Wait 没在 cancel 后返回 - 进程没杀干净")
	}
}

// resolve 归一化后的路径必须始终在 workDir 内（纯函数，不落盘）
func TestResolveStaysInWorkDir(t *testing.T) {
	c := newBox(t)

	for _, name := range []string{
		"../escape", "../../etc/passwd", "/etc/passwd",
		"a/../../b", "sub/../../../x",
	} {
		got, err := c.resolve(name)
		if err != nil { // 直接拒绝也算安全
			continue
		}
		rel, rerr := filepath.Rel(c.workDir, got)
		if rerr != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
			t.Errorf("resolve(%q)=%q 逃出了 workDir", name, got)
		}
	}
}

// 空命令要报错
func TestEmptyCommand(t *testing.T) {
	c := newBox(t)

	if _, err := c.Start(context.Background(), Spec{Command: []string{}}); err == nil {
		t.Fatal("start 空命令未报错")
	}
}

// 非 0 退出不被当成错误，退出码要如实透传
func TestNonZeroReturn(t *testing.T) {
	c := newBox(t)

	p, err := c.Start(context.Background(), Spec{Command: []string{"/bin/sh", "-c", "exit 7"}})
	if err != nil {
		t.Fatalf("非 0 退出被当成了错误: %v", err)
	}

	u, err := p.Wait(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if u.ExitCode != 7 {
		t.Fatalf("退出码为 %d, 不为 7", u.ExitCode)
	}
}

// workDir 内的可执行文件能被运行
func TestRunExecutableFile(t *testing.T) {
	c := newBox(t)

	// 必须带 shebang（否则 execve 报 exec format error），且 0o755 可读可执行
	name, body := "Main.sh", "#!/bin/sh\necho hello\n"
	if err := c.PutFile(name, strings.NewReader(body), 0o755); err != nil {
		t.Fatal(err)
	}

	var stdout, stderr bytes.Buffer
	// 不带 "/" 的名字 → Start 解析成 workDir 下的绝对路径
	p, err := c.Start(context.Background(), Spec{Command: []string{name}, Stdout: &stdout, Stderr: &stderr})
	if err != nil {
		t.Fatalf("Start: %v", err)
	}

	u, err := p.Wait(context.Background())
	if err != nil {
		t.Fatalf("Wait: %v", err)
	}
	if u.ExitCode != 0 {
		t.Errorf("退出码非 0: %d, stderr=%q", u.ExitCode, stderr.String())
	}
	if stdout.String() != "hello\n" { // echo 会带上换行
		t.Errorf("stdout=%q want %q", stdout.String(), "hello\n")
	}
}

// 资源统计：跑个能测到的 CPU 循环，CPUNs / MemoryBytes 都应 > 0
func TestCPUNsAndMemoryBytes(t *testing.T) {
	c := newBox(t)

	p, err := c.Start(context.Background(), Spec{
		Command: []string{"/bin/sh", "-c", "i=0; while [ $i -lt 100000 ]; do i=$((i+1)); done"},
	})
	if err != nil {
		t.Fatal(err)
	}

	u, err := p.Wait(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if u.CPUNs == 0 || u.MemoryBytes == 0 {
		t.Fatalf("资源统计失败, CPUNs=%d MemoryBytes=%d", u.CPUNs, u.MemoryBytes)
	}
}

// stdin 透传
func TestStdinPipe(t *testing.T) {
	c := newBox(t)

	var out bytes.Buffer
	p, err := c.Start(context.Background(), Spec{
		Command: []string{"/bin/cat"},
		Stdin:   strings.NewReader("ping"),
		Stdout:  &out,
	})
	if err != nil {
		t.Fatal(err)
	}

	if _, err := p.Wait(context.Background()); err != nil {
		t.Fatal(err)
	}
	if out.String() != "ping" {
		t.Errorf("stdin 未透传, got %q", out.String())
	}
}
