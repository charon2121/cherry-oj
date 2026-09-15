// Package workspace 管理服务独占的暂存根：启动时核验并回收遗留目录，
// 运行时为每次执行分配独立目录。它不理解执行本身。
package workspace

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

// OpenWorkspace 在独占锁下核验并恢复服务暂存根；失败时不接纳执行。
// 成功后由调用者在所有 Container 关闭后释放 Workspace。
func OpenWorkspace(root string) (*Workspace, error) {
	if root == "" {
		return nil, fmt.Errorf("the staging root must not be empty")
	}
	if err := os.MkdirAll(root, 0o700); err != nil {
		return nil, err
	}
	info, err := os.Lstat(root)
	if err != nil {
		return nil, err
	}
	if !owned(info) || !info.IsDir() || info.Mode().Perm() != 0o700 {
		return nil, fmt.Errorf("the staging root must be a 0700 directory owned by the service")
	}
	lock, err := os.OpenFile(filepath.Join(root, ".lock"), os.O_CREATE|os.O_RDWR|syscall.O_NOFOLLOW|syscall.O_NONBLOCK, 0o600)
	if err != nil {
		return nil, err
	}
	info, err = lock.Stat()
	if err == nil && (!owned(info) || !info.Mode().IsRegular() || info.Sys().(*syscall.Stat_t).Nlink != 1) {
		err = fmt.Errorf("the staging lock file is not safe")
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
			return fmt.Errorf("unknown entry in the staging root: %q", entry.Name())
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
				return fmt.Errorf("unknown file in the workspace: %q", file.Name())
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

// Root 返回暂存根路径，供执行后端在其中分配单次执行目录。
func (w *Workspace) Root() string { return w.root }

// Close 必须在容量池关闭之后；若仍有工作区残留，返回错误并保留证据。
func (w *Workspace) Close() error {
	entries, err := os.ReadDir(w.root)
	if err == nil && len(entries) != 1 {
		err = fmt.Errorf("the staging root still has unreclaimed workspaces")
	}
	return errors.Join(err, w.lock.Close())
}
