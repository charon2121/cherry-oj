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
		return nil, fmt.Errorf("cgroup 委派目录必须是绝对路径")
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
	const cgroup2Magic = 0x63677270
	if stat.Type != cgroup2Magic {
		return fail(fmt.Errorf("目录不在 cgroup v2 文件系统中"))
	}
	return &diskFilesystem{root: root}, nil
}
