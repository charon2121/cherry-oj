package helper

import (
	"errors"
	"io"
	"os"
	"os/exec"
	"sync"
)

// ownedFile 的地址代表唯一所有权；多个收尾路径可以关闭同一个句柄，但只执行一次 Close。
// 不在关闭后清空 File 指针，避免与阻塞读写 goroutine 产生数据竞争。
type ownedFile struct {
	*os.File
	once sync.Once
	err  error
}

func ownFile(f *os.File) *ownedFile {
	if f == nil {
		return nil
	}
	return &ownedFile{File: f}
}
func (f *ownedFile) Close() error {
	if f == nil {
		return nil
	}
	f.once.Do(func() { f.err = f.File.Close() })
	return f.err
}
func closeFiles(files ...*ownedFile) error {
	var result error
	for _, f := range files {
		result = errors.Join(result, f.Close())
	}
	return result
}
func pipe() (*ownedFile, *ownedFile, error) {
	r, w, err := os.Pipe()
	return ownFile(r), ownFile(w), err
}
func isProcessExit(err error) bool {
	var exit *exec.ExitError
	return err == nil || errors.As(err, &exit)
}

// copyInput 不进入 os.File.ReadFrom/net.Conn.WriteTo 的 splice 快路径。
// Linux标准库的splice缓存由GC关闭pipe，不能作为单次执行已回收的证据。
// 包装器只暴露Read/Write，io.CopyN用固定大小缓冲且短读返回错误。
func copyInput(dst io.Writer, src io.Reader, n int64) error {
	_, err := io.CopyN(struct{ io.Writer }{dst}, struct{ io.Reader }{src}, n)
	return err
}
