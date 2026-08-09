package store

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
)

// diskStore 把每袋字节存成 root 下的一个文件，文件名就是 ref。
// 于是 path 恒等于 root+ref——文件系统本身就是那张索引表，
// 不需要内存 map（它冗余、不耐进程重启、还会随漏删泄漏内存）。
type diskStore struct {
	root string
}

var ErrNotFound = errors.New("store reference not found")

// ref 只可能是 newID 生成的 32 位小写 hex。
// 这条正则同时是安全边界：它挡住 "../../etc/passwd" 这类路径穿越，
// 让「ref 拼进路径」这件事变得安全。
var refPattern = regexp.MustCompile(`^[0-9a-f]{32}$`)

func NewDiskStore() (*diskStore, error) {
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

	return &diskStore{root: absRoot}, nil
}

func newID() (string, error) {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "", fmt.Errorf("generate random ID: %w", err)
	}
	return hex.EncodeToString(buf), nil
}

// path 由 ref 直接算出落点，格式不合法的一律当成「找不到」。
func (s *diskStore) path(ref string) (string, error) {
	if !refPattern.MatchString(ref) {
		return "", fmt.Errorf("%w: %q", ErrNotFound, ref)
	}
	return filepath.Join(s.root, ref), nil
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

	success = true

	return ref, nil
}

func (s *diskStore) Get(ref string) (io.ReadCloser, error) {
	path, err := s.path(ref)
	if err != nil {
		return nil, err
	}

	file, err := os.Open(path)

	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return nil, fmt.Errorf("%w: %s", ErrNotFound, ref)
		}
		return nil, fmt.Errorf("open store reference %q: %w", ref, err)
	}

	return file, nil
}

func (s *diskStore) Delete(ref string) error {
	path, err := s.path(ref)
	if err != nil {
		return err
	}

	// 删一个已经不在的 ref 视为成功——Delete 要幂等，
	// 否则 judge 的清理路径重试一次就会报假错。
	if err := os.Remove(path); err != nil && !errors.Is(err, os.ErrNotExist) {
		return fmt.Errorf("delete store reference %q: %w", ref, err)
	}

	return nil
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
