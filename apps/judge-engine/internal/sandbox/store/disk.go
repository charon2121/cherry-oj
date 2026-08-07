package store

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync"
)

type diskStore struct {
	root string
	mu   sync.Mutex
	refs map[string]string // ref -> absolute path
}

var ErrNotFound = errors.New("store reference not found")

func NewdiskStore() (*diskStore, error) {
	root := defaultRoot()
	return NewDiskStoreWithRoot(root)
}

func NewDiskStoreWithRoot(root string) (*diskStore, error) {
	if root == "" {
		return nil, fmt.Errorf("store root is empty")
	}

	absRoot, err := filepath.Abs(root)

	if err != nil {
		return nil, fmt.Errorf("resolve store root %q: %w", root, err)
	}

	if err := os.MkdirAll(absRoot, 0o700); err != nil {
		return nil, fmt.Errorf("create store root %q: %w", root, err)
	}

	return &diskStore{
		root: absRoot,
		refs: make(map[string]string),
	}, nil
}

func newID() (string, error) {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "", fmt.Errorf("generate random ID: %w", err)
	}
	return hex.EncodeToString(buf), nil
}

func (s *diskStore) Put(r io.Reader) (string, error) {

	if r == nil {
		return "", fmt.Errorf("reader is nil")
	}

	ref, err := newID()

	if err != nil {
		return "", fmt.Errorf("generate store reference: %w", err)
	}

	path := filepath.Join(s.root, ref)

	file, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o600)

	if err != nil {
		return "", fmt.Errorf("create store file %q: %w", path, err)
	}

	success := false

	// 如果文件完整写入失败，需要清除破损的文件
	defer func() {
		_ = file.Close()
		if !success {
			_ = os.Remove(path)
		}
	}()

	if _, err := io.Copy(file, r); err != nil {
		return "", fmt.Errorf("write store file %q: %w", path, err)
	}

	if err := file.Close(); err != nil {
		return "", fmt.Errorf("close store file %q: %w", path, err)
	}

	s.mu.Lock()
	s.refs[ref] = path
	s.mu.Unlock()
	success = true

	return ref, nil
}

func (s *diskStore) Get(ref string) (io.ReadCloser, error) {
	path, err := s.lookup(ref)
	if err != nil {
		return nil, err
	}

	file, err := os.Open(path)

	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			// 映射文件存在，实际文件丢失，清理 ref 记录
			s.removeRefIfMatch(ref, path)
			return nil, fmt.Errorf("%w: %s", ErrNotFound, ref)
		}
		return nil, fmt.Errorf("open store reference %q: %w", ref, err)
	}

	return file, nil
}

func (s *diskStore) Delete(ref string) error {
	path, err := s.take(ref)
	if err != nil {
		return err
	}

	if err := os.Remove(path); err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return nil
		}

		// 删除失败恢复映射，方便重试
		s.mu.Lock()
		s.refs[ref] = path
		s.mu.Unlock()

		return fmt.Errorf("delete store reference %q: %w", ref, err)
	}

	return nil
}

func (s *diskStore) lookup(ref string) (string, error) {
	if ref == "" {
		return "", fmt.Errorf("%w: empty reference", ErrNotFound)
	}

	s.mu.Lock()
	path, ok := s.refs[ref]
	s.mu.Unlock()

	if !ok {
		return "", fmt.Errorf("%w: %s", ErrNotFound, ref)
	}
	return path, nil
}

func (s *diskStore) removeRefIfMatch(ref, path string) {
	s.mu.Lock()
	defer s.mu.Unlock()

	current, ok := s.refs[ref]
	if ok && current == path {
		delete(s.refs, ref)
	}
}

func (s *diskStore) take(ref string) (string, error) {
	if ref == "" {
		return "", fmt.Errorf("%w: empty reference", ErrNotFound)
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	path, ok := s.refs[ref]

	if !ok {
		return "", fmt.Errorf("%w: %s", ErrNotFound, ref)
	}

	delete(s.refs, ref)
	return path, nil
}

func defaultRoot() string {
	const sharedMemoryRoot = "/dev/shm/cherry-oj"
	if canUseDirectory(sharedMemoryRoot) {
		return sharedMemoryRoot
	}
	return filepath.Join(os.TempDir(), "cherry-oj")
}

func canUseDirectory(path string) bool {
	if err := os.MkdirAll(path, 0o700); err != nil {
		return false
	}

	probe, err := os.CreateTemp(path, ".probe-*")
	if err != nil {
		return false
	}
	name := probe.Name()
	if err := os.Remove(name); err != nil {
		return false
	}
	return true
}
