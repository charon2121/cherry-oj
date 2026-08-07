package store

import (
	"bytes"
	"errors"
	"io"
	"testing"
)

func TestDiskStorePutGetDelete(t *testing.T) {
	s, err := NewDiskStoreWithRoot(t.TempDir())

	if err != nil {
		t.Fatalf("create disk store: %v", err)
	}

	want := []byte("hello cherry oj")

	ref, err := s.Put(bytes.NewReader(want))

	if err != nil {
		t.Fatalf("put: %v", err)
	}

	if ref == "" {
		t.Fatalf("unexpected empty ref")
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
	s, err := NewDiskStoreWithRoot(t.TempDir())

	if err != nil {
		t.Fatalf("create disk store: %v", err)
	}

	err = s.Delete("unkown")

	if !errors.Is(err, ErrNotFound) {
		t.Fatalf("expected ErrNotFound, got %v", err)
	}
}

func TestDiskStorePutNilReader(t *testing.T) {
	s, err := NewDiskStoreWithRoot(t.TempDir())

	if err != nil {
		t.Fatalf("create disk store: %v", err)
	}

	if _, err := s.Put(nil); err == nil {
		t.Fatal("expected err for nil reader")
	}
}
