// 白盒测试需按ref替换目录条目，验证链接和重启恢复拒绝。
package store

import (
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"testing"
	"time"
)

func boundedStore(t *testing.T, opts Options) *diskStore {
	t.Helper()
	s, e := New(filepath.Join(t.TempDir(), "store"), opts)
	if e != nil {
		t.Fatal(e)
	}
	t.Cleanup(func() {
		if e := s.Close(); e != nil {
			t.Error(e)
		}
	})
	return s
}
func TestCapacityIncludesOpenDeletedFiles(t *testing.T) {
	s := boundedStore(t, Options{MaxBlobBytes: 4, MaxTotalBytes: 4, MaxEntries: 2, Retention: time.Hour})
	ref, e := s.Put(strings.NewReader("1234"))
	if e != nil {
		t.Fatal(e)
	}
	rc, e := s.Get(ref)
	if e != nil {
		t.Fatal(e)
	}
	if e = s.Delete(ref); e != nil {
		t.Fatal(e)
	}
	if _, e = s.Put(strings.NewReader("x")); !errors.Is(e, ErrCapacity) {
		t.Fatalf("unlinked open file not counted: %v", e)
	}
	if e = rc.Close(); e != nil {
		t.Fatal(e)
	}
	if _, e = s.Put(strings.NewReader("x")); e != nil {
		t.Fatal(e)
	}
}
func TestOversizeRollbackAndExpiry(t *testing.T) {
	s := boundedStore(t, Options{MaxBlobBytes: 4, MaxTotalBytes: 8, MaxEntries: 2, Retention: time.Hour})
	if _, e := s.Put(strings.NewReader("12345")); !errors.Is(e, ErrCapacity) {
		t.Fatalf("oversize=%v", e)
	}
	if s.used != 0 || len(s.entries) != 0 {
		t.Fatal("reservation leaked")
	}
	ref, e := s.Put(strings.NewReader("abc"))
	if e != nil {
		t.Fatal(e)
	}
	s.entries[ref].expires = time.Now().Add(-time.Second)
	if e = s.Sweep(); e != nil {
		t.Fatal(e)
	}
	if _, e = s.Get(ref); !errors.Is(e, ErrNotFound) {
		t.Fatalf("expired=%v", e)
	}
	if _, e = os.Stat(filepath.Join(s.root, ref)); !errors.Is(e, os.ErrNotExist) {
		t.Fatalf("expired file remains: %v", e)
	}
}
func TestStoreRejectsLinksAndSpecialFiles(t *testing.T) {
	for _, kind := range []string{"symlink", "hardlink", "fifo"} {
		t.Run(kind, func(t *testing.T) {
			s := boundedStore(t, Options{4, 8, 2, time.Hour})
			ref, e := s.Put(strings.NewReader("abc"))
			if e != nil {
				t.Fatal(e)
			}
			path := filepath.Join(s.root, ref)
			if e = os.Remove(path); e != nil {
				t.Fatal(e)
			}
			source := filepath.Join(t.TempDir(), "source")
			if e = os.WriteFile(source, []byte("abc"), 0o600); e != nil {
				t.Fatal(e)
			}
			switch kind {
			case "symlink":
				e = os.Symlink(source, path)
			case "hardlink":
				e = os.Link(source, path)
			case "fifo":
				e = syscall.Mkfifo(path, 0o600)
			}
			if e != nil {
				t.Fatal(e)
			}
			if rc, e := s.Get(ref); e == nil {
				rc.Close()
				t.Fatal("accepted unsafe file")
			}
		})
	}
}
func TestExclusiveRootAndPendingRecovery(t *testing.T) {
	s := boundedStore(t, Options{4, 8, 2, time.Hour})
	if other, e := New(s.root, s.opts); e == nil {
		other.Close()
		t.Fatal("shared root accepted")
	}
	path := filepath.Join(s.root, ".pending-0123456789abcdef0123456789abcdef")
	if e := os.WriteFile(path, []byte("part"), 0o600); e != nil {
		t.Fatal(e)
	}
	if e := s.Close(); e != nil {
		t.Fatal(e)
	}
	reopened, e := New(s.root, s.opts)
	if e != nil {
		t.Fatal(e)
	}
	defer reopened.Close()
	if _, e := os.Stat(path); !errors.Is(e, os.ErrNotExist) {
		t.Fatal("partial upload retained")
	}
	ref, e := reopened.Put(strings.NewReader("full"))
	if e != nil {
		t.Fatal(e)
	}
	rc, e := reopened.Get(ref)
	if e != nil {
		t.Fatal(e)
	}
	data, e := io.ReadAll(rc)
	closeErr := rc.Close()
	if string(data) != "full" || e != nil || closeErr != nil {
		t.Fatalf("read=%q %v %v", data, e, closeErr)
	}
}
