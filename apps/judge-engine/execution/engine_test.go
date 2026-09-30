package execution

import (
	"context"
	"io"
	"log/slog"
	"path/filepath"
	"strings"
	"testing"

	"cherry-oj/judge-engine/execution/backend"
	"cherry-oj/judge-engine/internal/contract"
)

func devhostEngine(t *testing.T) *Engine {
	t.Helper()
	s := DefaultEngineSettings()
	s.Backend, s.AllowUnsafeBackend = backend.NameDevHost, true
	s.Store.Root = filepath.Join(t.TempDir(), "blobs")
	e, err := Open(s, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := e.Close(); err != nil {
			t.Error(err)
		}
	})
	return e
}

// 进程内执行层要提供 judge 通过 HTTP 用过的全部能力：上传、按 ref 执行、删除、自报隔离后端。
func TestEngineUploadRunDelete(t *testing.T) {
	e := devhostEngine(t)
	ctx := context.Background()
	ref, err := e.Upload(ctx, strings.NewReader("hello\n"))
	if err != nil {
		t.Fatal(err)
	}
	res, err := e.Run(ctx, contract.RunSpec{Command: []string{"cat", "in.txt"},
		Inputs: map[string]contract.FileSource{"in.txt": {Ref: ref}}})
	if err != nil || res.Status != contract.StatusOK || res.Stdout != "hello\n" {
		t.Fatalf("res=%+v err=%v", res, err)
	}
	if err := e.Delete(ctx, ref); err != nil {
		t.Fatal(err)
	}
	// 删除是幂等的：flow 在收尾时会删除所有 ref，重复或已过期的 ref 不应变成错误。
	if err := e.Delete(ctx, ref); err != nil {
		t.Fatalf("deleting a missing ref: %v", err)
	}
	if v, _ := e.Version(ctx); v.Name != Name || v.Isolation != backend.NameDevHost {
		t.Fatalf("version = %+v", v)
	}
}

// HTTP 入口原来挡住的非法请求，进程内也必须挡住，而不是交给后端去猜。
func TestEngineRejectsInvalidRequests(t *testing.T) {
	e := devhostEngine(t)
	if _, err := e.Run(context.Background(), contract.RunSpec{}); err == nil {
		t.Fatal("empty command accepted")
	}
	if _, err := e.Run(context.Background(), contract.RunSpec{Command: []string{"true"},
		Limits: contract.Limits{CPUNs: -1}}); err == nil {
		t.Fatal("negative limit accepted")
	}
}

// Stopped 在关闭后必须可观察，装配方靠它决定进程是否还能在线。
func TestEngineStoppedAfterClose(t *testing.T) {
	e := devhostEngine(t)
	select {
	case <-e.Stopped():
		t.Fatal("stopped before close")
	default:
	}
	if err := e.Close(); err != nil {
		t.Fatal(err)
	}
	<-e.Stopped()
	if _, err := e.Run(context.Background(), contract.RunSpec{Command: []string{"true"}}); err == nil {
		t.Fatal("closed engine accepted work")
	}
}

// 装配失败时不能留下已打开的资源，也不能返回一个半成品。
func TestOpenFailureReleasesResources(t *testing.T) {
	s := DefaultEngineSettings()
	s.Backend = "unknown"
	s.Store.Root = filepath.Join(t.TempDir(), "blobs")
	if e, err := Open(s, slog.New(slog.NewTextHandler(io.Discard, nil))); err == nil || e != nil {
		t.Fatalf("e=%v err=%v", e, err)
	}
	// store 已被关闭并释放锁：同一目录可以再次打开。
	s.Backend, s.AllowUnsafeBackend = backend.NameDevHost, true
	e, err := Open(s, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("store was left locked: %v", err)
	}
	e.Close()
}
