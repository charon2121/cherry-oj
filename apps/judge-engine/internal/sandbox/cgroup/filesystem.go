package cgroup

import (
	"errors"
	"fmt"
	"io"
	"os"
)

const maxControlBytes = 16 << 10

type diskFilesystem struct{ root *os.Root }

func (d *diskFilesystem) read(name string) ([]byte, error) {
	f, err := d.root.Open(name)
	if err != nil {
		return nil, err
	}
	data, readErr := io.ReadAll(io.LimitReader(f, maxControlBytes+1))
	closeErr := f.Close()
	if len(data) > maxControlBytes {
		return nil, errors.Join(fmt.Errorf("cgroup 文件 %s 超过控制面读取上限", name), closeErr)
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
func (d *diskFilesystem) mkdir(name string) error            { return d.root.Mkdir(name, 0700) }
func (d *diskFilesystem) remove(name string) error           { return d.root.Remove(name) }
func (d *diskFilesystem) open(name string) (*os.File, error) { return d.root.Open(name) }
func (d *diskFilesystem) close() error                       { return d.root.Close() }

func (d *diskFilesystem) checkWritable(name string) error {
	f, err := d.root.OpenFile(name, os.O_WRONLY, 0)
	if err != nil {
		return err
	}
	return f.Close()
}
