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

// Reset 清空内容但保留工作目录本身 —— 这是它和 Close 的全部区别，
// 也是 pool 复用工作间（借出→用完→Reset→还池）的前提。
func TestResetClearsButKeepsWorkDir(t *testing.T) {
	c := newBox(t)

	if err := c.PutFile("main.cpp", strings.NewReader("int main(){}"), 0o644); err != nil {
		t.Fatal(err)
	}
	// 子目录也要一并清掉（Reset 用的是 RemoveAll，不是 Remove）
	if err := os.MkdirAll(filepath.Join(c.workDir, "sub", "deep"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := c.PutFile("sub/deep/a.txt", strings.NewReader("x"), 0o644); err != nil {
		t.Fatal(err)
	}

	if err := c.Reset(); err != nil {
		t.Fatalf("Reset: %v", err)
	}

	// ① 目录还在，而且还是个目录
	fi, err := os.Stat(c.workDir)
	if err != nil {
		t.Fatalf("Reset 把 workDir 本身删了: %v", err)
	}
	if !fi.IsDir() {
		t.Fatalf("workDir 不再是目录")
	}

	// ② 里面空了
	entries, err := os.ReadDir(c.workDir)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 0 {
		names := make([]string, 0, len(entries))
		for _, e := range entries {
			names = append(names, e.Name())
		}
		t.Errorf("Reset 后残留 %v", names)
	}
}

// Reset 之后工作间还能用 —— 光「目录还在」不够，得真能再跑一轮
func TestResetKeepsContainerUsable(t *testing.T) {
	c := newBox(t)

	if err := c.PutFile("Main.sh", strings.NewReader("#!/bin/sh\necho first\n"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := c.Reset(); err != nil {
		t.Fatalf("Reset: %v", err)
	}

	// 旧文件确实没了
	if _, err := c.GetFile("Main.sh"); err == nil {
		t.Errorf("Reset 后旧文件仍能读到")
	}

	// 重新铺一份并跑起来
	if err := c.PutFile("Main.sh", strings.NewReader("#!/bin/sh\necho second\n"), 0o755); err != nil {
		t.Fatalf("Reset 后 PutFile 失败: %v", err)
	}

	var stdout bytes.Buffer
	p, err := c.Start(context.Background(), Spec{Command: []string{"Main.sh"}, Stdout: &stdout})
	if err != nil {
		t.Fatalf("Reset 后 Start 失败: %v", err)
	}
	if _, err := p.Wait(context.Background()); err != nil {
		t.Fatal(err)
	}
	if stdout.String() != "second\n" {
		t.Errorf("stdout=%q want %q", stdout.String(), "second\n")
	}
}

// 连续 Reset 要成功（空目录上再 Reset 不是错误）
func TestResetIsIdempotent(t *testing.T) {
	c := newBox(t)

	for i := range 3 {
		if err := c.Reset(); err != nil {
			t.Fatalf("第 %d 次 Reset: %v", i+1, err)
		}
	}
}

// Close 删掉整个工作目录 —— 和 Reset 对照着看语义差别。
// Close 之后 Reset 必须报错：pool 的 giveBack 就靠这个信号丢弃坏掉的工作间。
func TestCloseRemovesWorkDir(t *testing.T) {
	c, err := NewHost() // 不用 newBox：这个用例要自己控制 Close 时机
	if err != nil {
		t.Fatalf("NewHost: %v", err)
	}
	workDir := c.workDir

	if err := c.Close(); err != nil {
		t.Fatalf("Close: %v", err)
	}

	if _, err := os.Stat(workDir); !os.IsNotExist(err) {
		t.Errorf("Close 后 workDir 仍在: err=%v", err)
	}
	if err := c.Reset(); err == nil {
		t.Errorf("Close 之后 Reset 应当报错，好让 pool 丢弃这个工作间")
	}
	// Close 幂等：pool 走异常路径时可能重复调
	if err := c.Close(); err != nil {
		t.Errorf("重复 Close 应当成功，got %v", err)
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
