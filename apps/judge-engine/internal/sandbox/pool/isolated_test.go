package pool_test

import (
	"context"
	"io"
	"net"
	"os"
	"path/filepath"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/container"
	"cherry-oj/judge-engine/internal/sandbox/helper"
	"cherry-oj/judge-engine/internal/sandbox/launcher"
	"cherry-oj/judge-engine/internal/sandbox/pool"
	"cherry-oj/judge-engine/internal/sandbox/store"
)

// 使用真实Unix协议、容量池、runner、Container与Store；只替代内核执行者。
// 两次执行证明编译产物发布后可用ref进入下一次全新执行，而不让runner理解helper协议。
func TestIsolatedArtifactRoundTrip(t *testing.T) {
	root, e := os.MkdirTemp("/tmp", "cherry-round-")
	if e != nil {
		t.Fatal(e)
	}
	defer os.RemoveAll(root)
	socket := filepath.Join(root, "s")
	listener, e := net.Listen("unix", socket)
	if e != nil {
		t.Fatal(e)
	}
	defer listener.Close()
	done := make(chan error, 1)
	go func() {
		for i := range 2 {
			conn, e := listener.Accept()
			if e != nil {
				done <- e
				return
			}
			conn.SetDeadline(time.Now().Add(3 * time.Second))
			e = func() error {
				var request launcher.Request
				if e := launcher.ReadFrame(conn, &request, launcher.MaxFrameBytes); e != nil {
					return e
				}
				input, e := io.ReadAll(io.LimitReader(conn, request.InputBytes()))
				if e != nil {
					return e
				}
				expected := "source"
				if i == 1 {
					expected = "binary"
				}
				if string(input) != expected {
					t.Errorf("request %d input=%q", i, input)
				}
				result := helper.Result{Version: 1, ClockNs: 10}
				if i == 0 {
					result.Outputs = []helper.Output{{Path: "program", SizeBytes: 6}}
				} else {
					result.Stdout = []byte("ok\n")
				}
				if e := launcher.WriteFrame(conn, result, 4<<20); e != nil {
					return e
				}
				if i == 0 {
					if _, e := io.WriteString(conn, "binary"); e != nil {
						return e
					}
				}
				return launcher.WriteFrame(conn, helper.Completion{Version: 1, Complete: true}, 1024)
			}()
			conn.Close()
			if e != nil {
				done <- e
				return
			}
		}
		done <- nil
	}()
	st, e := store.NewDiskStoreWithRoot(filepath.Join(root, "store"))
	if e != nil {
		t.Fatal(e)
	}
	defer st.Close()
	workspace, e := container.OpenWorkspace(filepath.Join(root, "work"))
	if e != nil {
		t.Fatal(e)
	}
	defer workspace.Close()
	p, e := pool.New(st, pool.Options{Parallelism: 1, QueueSize: 1, Factory: func() (container.Container, error) { return workspace.New(socket) }})
	if e != nil {
		t.Fatal(e)
	}
	defer p.Close()
	compile, e := p.Run(context.Background(), contract.RunSpec{Command: []string{"g++"}, Inputs: map[string]contract.FileSource{"main.cpp": {Text: "source"}}, Artifacts: []string{"program"}})
	if e != nil || compile.Status != contract.StatusOK || compile.Artifacts["program"] == "" {
		t.Fatalf("compile=%+v err=%v", compile, e)
	}
	run, e := p.Run(context.Background(), contract.RunSpec{Command: []string{"program"}, Inputs: map[string]contract.FileSource{"program": {Ref: compile.Artifacts["program"]}}})
	if e != nil || run.Status != contract.StatusOK || run.Stdout != "ok\n" {
		t.Fatalf("run=%+v err=%v", run, e)
	}
	if e := <-done; e != nil {
		t.Fatal(e)
	}
	entries, e := os.ReadDir(filepath.Join(root, "work"))
	if e != nil || len(entries) != 1 || entries[0].Name() != ".lock" {
		t.Fatalf("staging leaked: %v %v", entries, e)
	}
}
