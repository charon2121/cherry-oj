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
	"strings"
	"sync"
	"syscall"
	"time"
)

var ErrNotFound = errors.New("store reference not found")
var ErrCapacity = errors.New("store capacity exceeded")
var refPattern = regexp.MustCompile(`^[0-9a-f]{32}$`)

type Options struct {
	MaxBlobBytes  int64
	MaxTotalBytes int64
	MaxEntries    int
	Retention     time.Duration
}
type entry struct {
	size    int64
	expires time.Time
	readers int
	deleted bool
}
type diskStore struct {
	root    string
	dir     *os.Root
	lock    *os.File
	opts    Options
	mu      sync.Mutex
	entries map[string]*entry
	used    int64
	closed  bool
}

func NewDiskStore() (*diskStore, error) {
	root, e := os.MkdirTemp("", "cherry-store-")
	if e != nil {
		return nil, e
	}
	s, e := NewDiskStoreWithRoot(root)
	if e != nil {
		os.RemoveAll(root)
	}
	return s, e
}
func NewDiskStoreWithRoot(root string) (*diskStore, error) {
	return New(root, Options{64 << 20, 512 << 20, 4096, time.Hour})
}

// New 只接管显式私有目录；独占锁防止两个服务绕过各自的总量统计。
func New(root string, opts Options) (*diskStore, error) {
	if root == "" || opts.MaxBlobBytes <= 0 || opts.MaxBlobBytes > 64<<20 || opts.MaxTotalBytes < opts.MaxBlobBytes || opts.MaxEntries <= 0 || opts.Retention <= 0 {
		return nil, fmt.Errorf("store目录/容量/保留期无效")
	}
	abs, e := filepath.Abs(root)
	if e != nil {
		return nil, e
	}
	if e = os.MkdirAll(abs, 0o700); e != nil {
		return nil, e
	}
	info, e := os.Lstat(abs)
	if e != nil {
		return nil, e
	}
	if !info.IsDir() || info.Mode().Perm()&0o077 != 0 {
		return nil, fmt.Errorf("store必须是私有目录: %s", abs)
	}
	dir, e := os.OpenRoot(abs)
	if e != nil {
		return nil, e
	}
	lock, e := dir.OpenFile(".lock", os.O_CREATE|os.O_RDWR|syscall.O_NOFOLLOW|syscall.O_NONBLOCK, 0o600)
	if e != nil {
		dir.Close()
		return nil, e
	}
	if e = regular(lock); e == nil {
		e = syscall.Flock(int(lock.Fd()), syscall.LOCK_EX|syscall.LOCK_NB)
	}
	if e != nil {
		lock.Close()
		dir.Close()
		return nil, fmt.Errorf("store独占锁: %w", e)
	}
	s := &diskStore{root: abs, dir: dir, lock: lock, opts: opts, entries: map[string]*entry{}}
	if e = s.load(); e != nil {
		s.Close()
		return nil, e
	}
	return s, nil
}
func regular(f *os.File) error {
	info, e := f.Stat()
	if e != nil {
		return e
	}
	st, ok := info.Sys().(*syscall.Stat_t)
	if !info.Mode().IsRegular() || !ok || st.Nlink != 1 || int(st.Uid) != os.Geteuid() {
		return fmt.Errorf("store拒绝非独占普通文件: %s", f.Name())
	}
	return nil
}
func (s *diskStore) load() error {
	directory, e := s.dir.Open(".")
	if e != nil {
		return e
	}
	entries, e := directory.ReadDir(-1)
	e = errors.Join(e, directory.Close())
	if e != nil {
		return e
	}
	for _, d := range entries {
		name := d.Name()
		if name == ".lock" {
			continue
		}
		pending := strings.HasPrefix(name, ".pending-") && refPattern.MatchString(strings.TrimPrefix(name, ".pending-"))
		if !refPattern.MatchString(name) && !pending {
			return fmt.Errorf("store未知条目: %q", name)
		}
		f, e := s.dir.OpenFile(name, os.O_RDONLY|syscall.O_NOFOLLOW|syscall.O_NONBLOCK, 0)
		if e != nil {
			return e
		}
		e = regular(f)
		info, se := f.Stat()
		e = errors.Join(e, se, f.Close())
		if e != nil {
			return e
		}
		if pending {
			if e := s.dir.Remove(name); e != nil {
				return e
			}
			continue
		}
		expiry := info.ModTime().Add(s.opts.Retention)
		if time.Now().After(expiry) {
			if e := s.dir.Remove(name); e != nil {
				return e
			}
			continue
		}
		if len(s.entries) >= s.opts.MaxEntries || info.Size() > s.opts.MaxBlobBytes || info.Size() > s.opts.MaxTotalBytes-s.used {
			return ErrCapacity
		}
		s.entries[name] = &entry{size: info.Size(), expires: expiry}
		s.used += info.Size()
	}
	return nil
}
func newID() (string, error) {
	var b [16]byte
	if _, e := rand.Read(b[:]); e != nil {
		return "", e
	}
	return hex.EncodeToString(b[:]), nil
}
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
	s.mu.Lock()
	if s.closed {
		s.mu.Unlock()
		return "", os.ErrClosed
	}
	if e := s.sweep(); e != nil {
		s.mu.Unlock()
		return "", e
	}
	// 写入前预留整份上限；上传互不持锁等待IO。空文件也占条目配额。
	if len(s.entries) >= s.opts.MaxEntries || s.opts.MaxBlobBytes > s.opts.MaxTotalBytes-s.used {
		s.mu.Unlock()
		return "", ErrCapacity
	}
	ref, e := newID()
	if e != nil {
		s.mu.Unlock()
		return "", e
	}
	if _, err := s.dir.Lstat(ref); !errors.Is(err, os.ErrNotExist) {
		s.mu.Unlock()
		return "", fmt.Errorf("store目标ref冲突或不可检查: %s: %v", ref, err)
	}
	f, e := s.dir.OpenFile(".pending-"+ref, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if e != nil {
		s.mu.Unlock()
		return "", e
	}
	s.entries[ref] = &entry{size: s.opts.MaxBlobBytes, readers: 1, deleted: true}
	s.used += s.opts.MaxBlobBytes
	s.mu.Unlock()
	n, e := io.Copy(f, io.LimitReader(r, s.opts.MaxBlobBytes+1))
	e = errors.Join(e, f.Close())
	if n > s.opts.MaxBlobBytes {
		e = errors.Join(e, ErrCapacity)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		e = errors.Join(e, os.ErrClosed)
	}
	if e == nil {
		e = s.dir.Rename(".pending-"+ref, ref)
	}
	if e != nil {
		removeErr := s.dir.Remove(".pending-" + ref)
		if removeErr != nil {
			return "", errors.Join(e, removeErr)
		}
		s.used -= s.entries[ref].size
		delete(s.entries, ref)
		return "", e
	}
	s.used -= s.opts.MaxBlobBytes - n
	s.entries[ref] = &entry{size: n, expires: time.Now().Add(s.opts.Retention)}
	return ref, nil
}
func (s *diskStore) Get(ref string) (io.ReadCloser, error) {
	if _, e := s.path(ref); e != nil {
		return nil, e
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		return nil, os.ErrClosed
	}
	x, ok := s.entries[ref]
	if !ok || x.deleted {
		return nil, ErrNotFound
	}
	if time.Now().After(x.expires) {
		return nil, errors.Join(ErrNotFound, s.remove(ref, x))
	}
	f, e := s.dir.OpenFile(ref, os.O_RDONLY|syscall.O_NOFOLLOW|syscall.O_NONBLOCK, 0)
	if e != nil {
		return nil, e
	}
	if e = regular(f); e != nil {
		f.Close()
		return nil, e
	}
	info, e := f.Stat()
	if e != nil || info.Size() != x.size {
		f.Close()
		return nil, fmt.Errorf("store条目大小改变: %s", ref)
	}
	x.readers++
	return &reader{File: f, release: func() {
		s.mu.Lock()
		defer s.mu.Unlock()
		x.readers--
		if x.deleted && x.readers == 0 {
			s.used -= x.size
			delete(s.entries, ref)
		}
	}}, nil
}
func (s *diskStore) Delete(ref string) error {
	if _, e := s.path(ref); e != nil {
		return e
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		return os.ErrClosed
	}
	if x := s.entries[ref]; x != nil && !x.deleted {
		return s.remove(ref, x)
	}
	return nil
}
func (s *diskStore) remove(ref string, x *entry) error {
	if e := s.dir.Remove(ref); e != nil && !errors.Is(e, os.ErrNotExist) {
		return e
	}
	x.deleted = true
	if x.readers == 0 {
		s.used -= x.size
		delete(s.entries, ref)
	}
	return nil
}
func (s *diskStore) sweep() error {
	for ref, x := range s.entries {
		if !x.deleted && time.Now().After(x.expires) {
			if e := s.remove(ref, x); e != nil {
				return e
			}
		}
	}
	return nil
}

// Sweep 由服务定时调用；已打开的读句柄仍计入总量，直到最后一个读者关闭。
func (s *diskStore) Sweep() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		return os.ErrClosed
	}
	return s.sweep()
}

// Close 必须在HTTP与执行池全部退出后调用，不能关闭仍有上传/读取的Store。
func (s *diskStore) Close() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		return nil
	}
	for _, x := range s.entries {
		if x.readers > 0 {
			return fmt.Errorf("store仍有在途文件操作")
		}
	}
	s.closed = true
	return errors.Join(s.lock.Close(), s.dir.Close())
}

type reader struct {
	*os.File
	once    sync.Once
	release func()
	err     error
}

func (r *reader) Close() error {
	r.once.Do(func() { r.err = r.File.Close(); r.release() })
	return r.err
}
