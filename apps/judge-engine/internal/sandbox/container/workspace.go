package container

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"syscall"
)

// Workspace 持有服务暂存根的独占锁。进程退出后可回收上一次只由本组件创建的普通文件。
// 用户进程从不挂载此目录；先校验全部条目，再清理，未知文件保留并拒绝启动。
type Workspace struct {
	root string
	lock *os.File
}

var executionName = regexp.MustCompile(`^execution-[0-9]+$`)
var dataName = regexp.MustCompile(`^data-[0-9]+$`)

func OpenWorkspace(root string) (*Workspace, error) {
	if root == "" {
		return nil, fmt.Errorf("暂存根不能为空")
	}
	if err := os.MkdirAll(root, 0o700); err != nil {
		return nil, err
	}
	info, err := os.Lstat(root)
	if err != nil {
		return nil, err
	}
	if !owned(info) || !info.IsDir() || info.Mode().Perm() != 0o700 {
		return nil, fmt.Errorf("暂存根必须是服务所有的0700目录")
	}
	lock, err := os.OpenFile(filepath.Join(root, ".lock"), os.O_CREATE|os.O_RDWR|syscall.O_NOFOLLOW|syscall.O_NONBLOCK, 0o600)
	if err != nil {
		return nil, err
	}
	info, err = lock.Stat()
	if err == nil && (!owned(info) || !info.Mode().IsRegular() || info.Sys().(*syscall.Stat_t).Nlink != 1) {
		err = fmt.Errorf("暂存锁文件不安全")
	}
	if err == nil {
		err = syscall.Flock(int(lock.Fd()), syscall.LOCK_EX|syscall.LOCK_NB)
	}
	if err != nil {
		lock.Close()
		return nil, err
	}
	w := &Workspace{root: root, lock: lock}
	if err := w.recover(); err != nil {
		lock.Close()
		return nil, err
	}
	return w, nil
}
func owned(info os.FileInfo) bool {
	stat, ok := info.Sys().(*syscall.Stat_t)
	return ok && int(stat.Uid) == os.Geteuid()
}
func (w *Workspace) recover() error {
	entries, err := os.ReadDir(w.root)
	if err != nil {
		return err
	}
	for _, entry := range entries {
		if entry.Name() == ".lock" {
			continue
		}
		info, e := entry.Info()
		if e != nil {
			return e
		}
		if !executionName.MatchString(entry.Name()) || !info.IsDir() || !owned(info) || info.Mode().Perm() != 0o700 {
			return fmt.Errorf("暂存根存在未知条目: %q", entry.Name())
		}
		files, e := os.ReadDir(filepath.Join(w.root, entry.Name()))
		if e != nil {
			return e
		}
		for _, file := range files {
			info, e := file.Info()
			if e != nil {
				return e
			}
			if !dataName.MatchString(file.Name()) || !owned(info) || !info.Mode().IsRegular() || info.Mode().Perm() != 0o600 || info.Sys().(*syscall.Stat_t).Nlink != 1 {
				return fmt.Errorf("工作区存在未知文件: %q", file.Name())
			}
		}
	}
	for _, entry := range entries {
		if entry.Name() != ".lock" {
			if err := os.RemoveAll(filepath.Join(w.root, entry.Name())); err != nil {
				return err
			}
		}
	}
	return nil
}
func (w *Workspace) New(socket string) (Container, error) { return NewIsolated(socket, w.root) }

// Close 必须在容量池关闭之后；若仍有工作区残留，返回错误并保留证据。
func (w *Workspace) Close() error {
	entries, err := os.ReadDir(w.root)
	if err == nil && len(entries) != 1 {
		err = fmt.Errorf("暂存根仍有未回收工作区")
	}
	return errors.Join(err, w.lock.Close())
}
