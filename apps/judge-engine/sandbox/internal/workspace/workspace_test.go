package workspace_test

import (
	"os"
	"path/filepath"
	"testing"

	"cherry-oj/judge-engine/sandbox/internal/workspace"
)

func TestWorkspaceRecoveryAndExclusivity(t *testing.T) {
	root := filepath.Join(t.TempDir(), "work")
	w, e := workspace.OpenWorkspace(root)
	if e != nil {
		t.Fatal(e)
	}
	if other, e := workspace.OpenWorkspace(root); e == nil {
		other.Close()
		t.Fatal("shared root allowed")
	}
	// 单次执行目录由执行后端在暂存根内分配；这里只验证分配与回收后暂存根仍然干净。
	execution, e := os.MkdirTemp(w.Root(), "execution-")
	if e != nil {
		t.Fatal(e)
	}
	if e := os.RemoveAll(execution); e != nil {
		t.Fatal(e)
	}
	if e := w.Close(); e != nil {
		t.Fatal(e)
	}
	dir := filepath.Join(root, "execution-123")
	if e := os.Mkdir(dir, 0o700); e != nil {
		t.Fatal(e)
	}
	if e := os.WriteFile(filepath.Join(dir, "data-123"), []byte("partial"), 0o600); e != nil {
		t.Fatal(e)
	}
	w, e = workspace.OpenWorkspace(root)
	if e != nil {
		t.Fatal(e)
	}
	if _, e := os.Stat(dir); !os.IsNotExist(e) {
		t.Fatal("stale execution retained")
	}
	if e := w.Close(); e != nil {
		t.Fatal(e)
	}
	if e := os.WriteFile(filepath.Join(root, "keep"), []byte("unknown"), 0o600); e != nil {
		t.Fatal(e)
	}
	if other, e := workspace.OpenWorkspace(root); e == nil {
		other.Close()
		t.Fatal("unknown entry allowed")
	}
	if _, e := os.Stat(filepath.Join(root, "keep")); e != nil {
		t.Fatal("unknown entry deleted")
	}
}
