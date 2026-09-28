//go:build linux && amd64

package cgroup

import (
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"syscall"
)

const maxControlBytes = 16 << 10

// 根句柄把后续相对访问绑定到已核验的 cgroup2fs，不反复按宿主路径解析执行组。
type diskFilesystem struct{ root *os.Root }

func (d *diskFilesystem) read(name string) ([]byte, error) {
	f, err := d.root.Open(name)
	if err != nil {
		return nil, err
	}
	data, readErr := io.ReadAll(io.LimitReader(f, maxControlBytes+1))
	closeErr := f.Close()
	if len(data) > maxControlBytes {
		return nil, errors.Join(fmt.Errorf("cgroup file %s exceeds the control-plane read limit", name), closeErr)
	}
	return data, errors.Join(readErr, closeErr)
}

func (d *diskFilesystem) write(name, value string) error {
	// 不使用 O_CREATE/O_TRUNC；缺少内核接口时必须失败，不能写出普通文件冒充控制器。
	f, err := d.root.OpenFile(name, os.O_WRONLY, 0)
	if err != nil {
		return err
	}
	n, writeErr := f.WriteString(value)
	if n != len(value) && writeErr == nil {
		writeErr = io.ErrShortWrite
	}
	return errors.Join(writeErr, f.Close())
}

func (d *diskFilesystem) mkdir(name string) error { return d.root.Mkdir(name, 0700) }

func (d *diskFilesystem) remove(name string) error { return d.root.Remove(name) }

func (d *diskFilesystem) open(name string) (*os.File, error) { return d.root.Open(name) }

func (d *diskFilesystem) close() error { return d.root.Close() }

func (d *diskFilesystem) checkWritable(name string) error {
	f, err := d.root.OpenFile(name, os.O_WRONLY, 0)
	if err != nil {
		return err
	}
	return f.Close()
}

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
