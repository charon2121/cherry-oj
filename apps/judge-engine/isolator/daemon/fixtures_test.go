//go:build linux && amd64

// 这些夹具供 daemon 的连接收尾测试使用；execution 持有自己的 testRequest。
// 非特权侧 Call 的用例在 internal/hostexec/client，两侧各自持有夹具副本。
package daemon

import (
	"net"
	"os"
	"path/filepath"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
)

func testRequest() hostexec.Request {
	return hostexec.Request{Version: 1, Command: []string{"main"}, Limits: contract.ExplicitLimits(contract.Limits{CPUNs: 1e9, ClockNs: 2e9, MemoryBytes: 64 << 20, MaxProcesses: 32, StdoutMaxBytes: 1024, StderrMaxBytes: 1024})}
}

func fakeServer(t *testing.T, serve func(net.Conn)) string {
	t.Helper()
	// macOS 的 t.TempDir 路径可能超过 sockaddr_un 上限，使用独占短目录。
	dir, err := os.MkdirTemp("/tmp", "cherry-helper-")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := os.RemoveAll(dir); err != nil {
			t.Error(err)
		}
	})
	path := filepath.Join(dir, "s")
	l, err := net.Listen("unix", path)
	if err != nil {
		t.Fatal(err)
	}
	done := make(chan struct{})
	go func() {
		defer close(done)
		c, err := l.Accept()
		if err != nil {
			return
		}
		defer c.Close()
		c.SetDeadline(time.Now().Add(3 * time.Second))
		serve(c)
	}()
	t.Cleanup(func() { l.Close(); <-done })
	return path
}

func writeTestCompletion(t *testing.T, c net.Conn) {
	t.Helper()
	var request hostexec.Request
	if err := hostexec.ReadFrame(c, &request, hostexec.MaxFrameBytes); err != nil {
		t.Error(err)
		return
	}
	if err := hostexec.WriteFrame(c, hostexec.Result{Version: hostexec.Version}, 4<<20); err != nil {
		t.Error(err)
		return
	}
	if err := hostexec.WriteFrame(c, hostexec.Completion{Version: hostexec.Version, Complete: true}, 1024); err != nil {
		t.Error(err)
	}
}
