package store

import (
	"bytes"
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// newStore 建一个只属于本用例的 store，t.TempDir 会自动清理。
func newStore(t *testing.T) *diskStore {
	t.Helper()
	s, err := NewDiskStoreWithRoot(t.TempDir())
	if err != nil {
		t.Fatalf("create disk store: %v", err)
	}
	return s
}

func TestDiskStorePutGetDelete(t *testing.T) {
	s := newStore(t)

	want := []byte("hello cherry oj")

	ref, err := s.Put(bytes.NewReader(want))

	if err != nil {
		t.Fatalf("put: %v", err)
	}

	if ref == "" {
		t.Fatalf("unexpected empty ref")
	}

	// ref 必须直接就是 root 下的文件名——没有内存索引，路径就是这么算出来的
	if _, err := os.Stat(filepath.Join(s.root, ref)); err != nil {
		t.Fatalf("ref %q 没有对应到 root 下的文件: %v", ref, err)
	}

	reader, err := s.Get(ref)

	if err != nil {
		t.Fatalf("Get: %v", err)
	}

	got, err := io.ReadAll(reader)

	if err != nil {
		_ = reader.Close()
		t.Fatalf("read: %v", err)
	}

	if err := reader.Close(); err != nil {
		t.Fatalf("close: %v", err)
	}

	if !bytes.Equal(got, want) {
		t.Fatalf("unexpected content: got %q, want %q", got, want)
	}

	if err := s.Delete(ref); err != nil {
		t.Fatalf("delete: %v", err)
	}

	_, err = s.Get(ref)
	if !errors.Is(err, ErrNotFound) {
		t.Fatalf("unexpeced ErrNotFound after delete, got %v", err)
	}
}

func TestDiskStoreDeleteUnknowRef(t *testing.T) {
	s := newStore(t)

	err := s.Delete("unkown")

	if !errors.Is(err, ErrNotFound) {
		t.Fatalf("expected ErrNotFound, got %v", err)
	}
}

// 删一个格式合法但已经不存在的 ref 要成功——Delete 必须幂等
func TestDiskStoreDeleteIsIdempotent(t *testing.T) {
	s := newStore(t)

	ref, err := s.Put(strings.NewReader("bye"))
	if err != nil {
		t.Fatal(err)
	}

	if err := s.Delete(ref); err != nil {
		t.Fatalf("first delete: %v", err)
	}
	if err := s.Delete(ref); err != nil {
		t.Fatalf("second delete 应该幂等成功，got %v", err)
	}
}

// 畸形 / 穿越用的 ref 一律拒绝，且绝不能碰到 root 之外的文件
func TestDiskStoreRejectsMalformedRef(t *testing.T) {
	s := newStore(t)

	bad := []string{
		"",
		"unkown",
		"../../etc/passwd",
		"/etc/passwd",
		"ABCDEF0123456789abcdef0123456789",  // 大写不合法
		"0123456789abcdef0123456789abcde",   // 31 位，少一位
		"0123456789abcdef0123456789abcdef0", // 33 位，多一位
	}

	for _, ref := range bad {
		t.Run(ref, func(t *testing.T) {
			if _, err := s.Get(ref); !errors.Is(err, ErrNotFound) {
				t.Errorf("Get(%q) = %v, want ErrNotFound", ref, err)
			}
			if err := s.Delete(ref); !errors.Is(err, ErrNotFound) {
				t.Errorf("Delete(%q) = %v, want ErrNotFound", ref, err)
			}
		})
	}
}

func TestDiskStorePutNilReader(t *testing.T) {
	s := newStore(t)

	if _, err := s.Put(nil); err == nil {
		t.Fatal("expected err for nil reader")
	}
}
