package backend_test

import (
	"context"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/sandbox/internal/backend"
	"cherry-oj/judge-engine/sandbox/internal/workspace"
)

func socketServer(t *testing.T, serve func(net.Conn)) string {
	t.Helper()
	// macOS 的 t.TempDir 路径可能超过 sockaddr_un 上限，使用独占短目录。
	dir, e := os.MkdirTemp("/tmp", "cherry-wire-")
	if e != nil {
		t.Fatal(e)
	}
	path := filepath.Join(dir, "s")
	l, e := net.Listen("unix", path)
	if e != nil {
		os.RemoveAll(dir)
		t.Fatal(e)
	}
	done := make(chan struct{})
	go func() {
		defer close(done)
		c, e := l.Accept()
		if e != nil {
			return
		}
		defer c.Close()
		c.SetDeadline(time.Now().Add(3 * time.Second))
		serve(c)
	}()
	t.Cleanup(func() { l.Close(); <-done; os.RemoveAll(dir) })
	return path
}

func wireJob() backend.Job {
	return backend.Job{Command: []string{"g++"}, Outputs: []string{"program"},
		Limits: contract.ExplicitLimits(contract.Limits{CPUNs: 1e9, ClockNs: 2e9,
			MemoryBytes: 64 << 20, MaxProcesses: 64, StdoutMaxBytes: 128, StderrMaxBytes: 128})}
}

// isolatedOn 在一个独占暂存根上创建后端，并在用例结束时确认暂存根已经空了。
func isolatedOn(t *testing.T, socket string) (*backend.Isolated, string) {
	t.Helper()
	root := filepath.Join(t.TempDir(), "staging")
	if e := os.Mkdir(root, 0o700); e != nil {
		t.Fatal(e)
	}
	ws, e := workspace.OpenWorkspace(root)
	if e != nil {
		t.Fatal(e)
	}
	t.Cleanup(func() {
		if e := ws.Close(); e != nil {
			t.Errorf("暂存根未回收干净: %v", e)
		}
	})
	b, e := backend.NewIsolated(socket, ws)
	if e != nil {
		t.Fatal(e)
	}
	return b, root
}

// 缺少完成尾帧时不得交付产物：协议帧正确不等于对端已经确认回收。
func TestIsolatedPublishesOnlyAfterCompletion(t *testing.T) {
	for _, complete := range []bool{false, true} {
		t.Run(map[bool]string{false: "missing completion", true: "complete"}[complete], func(t *testing.T) {
			socket := socketServer(t, func(conn net.Conn) {
				var r hostexec.Request
				if e := hostexec.ReadFrame(conn, &r, hostexec.MaxFrameBytes); e != nil {
					t.Error(e)
					return
				}
				b, e := io.ReadAll(io.LimitReader(conn, r.InputBytes()))
				if e != nil || string(b) != "sourceinput" {
					t.Errorf("input=%q err=%v", b, e)
				}
				if len(r.Inputs) != 1 || r.Inputs[0].Path != "main.cpp" || r.StdinBytes != 5 {
					t.Errorf("request=%+v", r)
				}
				if e := hostexec.WriteFrame(conn, hostexec.Result{Version: 1,
					Outputs: []hostexec.Output{{Path: "program", SizeBytes: 3}}}, 4<<20); e != nil {
					t.Error(e)
					return
				}
				if _, e := io.WriteString(conn, "elf"); e != nil {
					t.Error(e)
					return
				}
				if complete {
					if e := hostexec.WriteFrame(conn, hostexec.Completion{Version: 1, Complete: true}, 1024); e != nil {
						t.Error(e)
					}
				}
			})
			b, root := isolatedOn(t, socket)
			job := wireJob()
			job.Inputs = []backend.NamedSource{{Name: "main.cpp", Source: backend.Source{Reader: strings.NewReader("source")}}}
			job.Stdin = &backend.Source{Reader: strings.NewReader("input")}

			delivered := map[string]string{}
			_, err := b.Execute(context.Background(), job, func(_ backend.Facts, name string, r io.Reader) error {
				data, e := io.ReadAll(r)
				delivered[name] = string(data)
				return e
			})
			if (err == nil) != complete {
				t.Fatalf("Execute=%v complete=%v", err, complete)
			}
			if got, ok := delivered["program"]; ok != complete || (complete && got != "elf") {
				t.Fatalf("交付=%q 出现=%v complete=%v", got, ok, complete)
			}
			// Execute 返回即代表暂存目录已回收，不存在需要调用方再关闭的对象。
			files, e := os.ReadDir(root)
			if e != nil || len(files) != 1 { // 只剩 .lock
				t.Fatalf("residue=%v %v", files, e)
			}
		})
	}
}

// 请求取消后必须让对端观察到断连，并且不得交付任何产物。
func TestIsolatedCancellationClosesConnection(t *testing.T) {
	disconnected := make(chan struct{})
	socket := socketServer(t, func(c net.Conn) {
		var r hostexec.Request
		if e := hostexec.ReadFrame(c, &r, hostexec.MaxFrameBytes); e != nil {
			return
		}
		io.Copy(io.Discard, c)
		close(disconnected)
	})
	b, _ := isolatedOn(t, socket)
	job := wireJob()
	job.Outputs = nil

	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()
	if _, err := b.Execute(ctx, job, func(backend.Facts, string, io.Reader) error {
		t.Error("取消的执行交付了产物")
		return nil
	}); err == nil {
		t.Fatal("cancel returned success")
	}
	select {
	case <-disconnected:
	case <-time.After(time.Second):
		t.Fatal("socket retained")
	}
}

// 内存超限结论要求本任务的 OOM 证据：只有受害者计数不足以断定是这条命令超了限。
func TestIsolatedRequiresTaskLocalOOM(t *testing.T) {
	for _, taskOOM := range []uint64{0, 1} {
		socket := socketServer(t, func(conn net.Conn) {
			var r hostexec.Request
			if err := hostexec.ReadFrame(conn, &r, hostexec.MaxFrameBytes); err != nil {
				t.Error(err)
				return
			}
			result := hostexec.Result{Version: 1, Usage: hostexec.Usage{OOM: taskOOM, OOMKill: 1}}
			if err := hostexec.WriteFrame(conn, result, 4<<20); err != nil {
				t.Error(err)
				return
			}
			if err := hostexec.WriteFrame(conn, hostexec.Completion{Version: 1, Complete: true}, hostexec.MaxFrameBytes); err != nil {
				t.Error(err)
			}
		})
		b, _ := isolatedOn(t, socket)
		job := wireJob()
		job.Outputs = nil
		facts, err := b.Execute(context.Background(), job, nil)
		if err != nil || facts.OOMKilled != (taskOOM > 0) {
			t.Fatalf("OOM=%d facts=%+v err=%v", taskOOM, facts, err)
		}
	}
}

// 越界或重复的输入路径必须被拒绝，且不得把请求发给对端。
func TestIsolatedRejectsInvalidInputPaths(t *testing.T) {
	b, _ := isolatedOn(t, filepath.Join(t.TempDir(), "never-dialed.sock"))
	for _, inputs := range [][]backend.NamedSource{
		{{Name: "../escape", Source: backend.Source{Reader: strings.NewReader("x")}}},
		{{Name: "main.cpp", Source: backend.Source{Reader: strings.NewReader("x")}},
			{Name: "main.cpp", Source: backend.Source{Reader: strings.NewReader("y")}}},
	} {
		job := wireJob()
		job.Inputs = inputs
		if _, err := b.Execute(context.Background(), job, nil); err == nil {
			t.Fatalf("接受了无效输入: %+v", inputs)
		}
	}
}
