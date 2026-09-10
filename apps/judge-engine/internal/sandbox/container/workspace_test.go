package container_test

import (
	"os"
	"path/filepath"
	"testing"

	"cherry-oj/judge-engine/internal/sandbox/container"
)

func TestWorkspaceRecoveryAndExclusivity(t *testing.T) {
	root := filepath.Join(t.TempDir(), "work")
	w, e := container.OpenWorkspace(root)
	if e != nil {
		t.Fatal(e)
	}
	if other, e := container.OpenWorkspace(root); e == nil {
		other.Close()
		t.Fatal("shared root allowed")
	}
	c, e := w.New("unused")
	if e != nil {
		t.Fatal(e)
	}
	if e := c.Close(); e != nil {
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
	w, e = container.OpenWorkspace(root)
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
	if other, e := container.OpenWorkspace(root); e == nil {
		other.Close()
		t.Fatal("unknown entry allowed")
	}
	if _, e := os.Stat(filepath.Join(root, "keep")); e != nil {
		t.Fatal("unknown entry deleted")
	}
}
