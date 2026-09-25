//go:build linux && amd64

package cgroup

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"syscall"
)

func openFilesystem(path string) (filesystem, error) {
	if !filepath.IsAbs(path) {
		return nil, fmt.Errorf("cgroup delegation directory must be an absolute path")
	}
	root, err := os.OpenRoot(path)
	if err != nil {
		return nil, err
	}
	fail := func(err error) (filesystem, error) { return nil, errors.Join(err, root.Close()) }
	f, err := root.Open(".")
	if err != nil {
		return fail(err)
	}
	var stat syscall.Statfs_t
	statErr := syscall.Fstatfs(int(f.Fd()), &stat)
	closeErr := f.Close()
	if err = errors.Join(statErr, closeErr); err != nil {
		return fail(err)
	}
	// cgroup2fs 的文件系统魔数；仅目录名和控制文件名称相似不能证明它是内核控制器。
	const cgroup2Magic = 0x63677270
	if stat.Type != cgroup2Magic {
		return fail(fmt.Errorf("directory is not on a cgroup v2 filesystem"))
	}
	return &diskFilesystem{root: root}, nil
}
