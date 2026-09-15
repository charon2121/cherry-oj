package container_test

import (
	"context"
	"errors"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/sandbox/internal/container"
)

func socketServer(t *testing.T, serve func(net.Conn)) string {
	t.Helper()
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
func wireSpec() container.Spec {
	return container.Spec{Command: []string{"g++"}, Limits: contract.ExplicitLimits(contract.Limits{CPUNs: 1e9, ClockNs: 2e9, MemoryBytes: 64 << 20, MaxProcesses: 64, StdoutMaxBytes: 128, StderrMaxBytes: 128}), Outputs: []string{"program"}}
}
func privateRoot(t *testing.T) string {
	t.Helper()
	p := filepath.Join(t.TempDir(), "staging")
	if e := os.Mkdir(p, 0o700); e != nil {
		t.Fatal(e)
	}
	return p
}

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
				if e := hostexec.WriteFrame(conn, hostexec.Result{Version: 1, Outputs: []hostexec.Output{{Path: "program", SizeBytes: 3}}}, 4<<20); e != nil {
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
			root := privateRoot(t)
			c, e := container.NewIsolated(socket, root)
			if e != nil {
				t.Fatal(e)
			}
			t.Cleanup(func() {
				if e := c.Close(); e != nil {
					t.Error(e)
				}
			})
			if e := c.PutFile("../escape", strings.NewReader("x"), 0o755); e == nil {
				t.Fatal("accepted escape")
			}
			if e := c.PutFile("main.cpp", strings.NewReader("source"), 0o644); e != nil {
				t.Fatal(e)
			}
			s := wireSpec()
			s.Stdin = strings.NewReader("input")
			p, e := c.Start(context.Background(), s)
			if e != nil {
				t.Fatal(e)
			}
			_, e = p.Wait(context.Background())
			if (e == nil) != complete {
				t.Fatalf("Wait=%v complete=%v", e, complete)
			}
			file, e := c.GetFile("program")
			if (e == nil) != complete {
				t.Fatalf("GetFile=%v complete=%v", e, complete)
			}
			if complete {
				b, e := io.ReadAll(file)
				closeErr := file.Close()
				if string(b) != "elf" || e != nil || closeErr != nil {
					t.Fatalf("artifact=%q %v %v", b, e, closeErr)
				}
			}
			if _, e := c.Start(context.Background(), s); e == nil {
				t.Fatal("reused execution")
			}
			if e := c.Close(); e != nil {
				t.Fatal(e)
			}
			files, e := os.ReadDir(root)
			if e != nil || len(files) != 0 {
				t.Fatalf("residue=%v %v", files, e)
			}
		})
	}
}
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
	c, e := container.NewIsolated(socket, privateRoot(t))
	if e != nil {
		t.Fatal(e)
	}
	s := wireSpec()
	s.Outputs = nil
	p, e := c.Start(context.Background(), s)
	if e != nil {
		t.Fatal(e)
	}
	// 等连接建立再取消，避免只覆盖Dial取消。
	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()
	_, e = p.Wait(ctx)
	if e == nil {
		t.Fatal("cancel returned success")
	}
	if e := c.Close(); e != nil {
		t.Fatal(e)
	}
	select {
	case <-disconnected:
	case <-time.After(time.Second):
		t.Fatal("socket retained")
	}
	if _, e := c.GetFile("program"); e == nil || errors.Is(e, io.EOF) {
		t.Fatal("closed workspace published")
	}
}

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
		c, err := container.NewIsolated(socket, privateRoot(t))
		if err != nil {
			t.Fatal(err)
		}
		spec := wireSpec()
		spec.Outputs = nil
		p, err := c.Start(context.Background(), spec)
		if err != nil {
			c.Close()
			t.Fatal(err)
		}
		usage, err := p.Wait(context.Background())
		closeErr := c.Close()
		if err != nil || closeErr != nil || usage.OOMKilled != (taskOOM > 0) {
			t.Fatalf("OOM=%d usage=%+v err=%v close=%v", taskOOM, usage, err, closeErr)
		}
	}
}
