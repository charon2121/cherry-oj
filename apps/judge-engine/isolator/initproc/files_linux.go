//go:build linux && amd64

package initproc

import (
	"errors"
	"fmt"
	"io"
	"os"
	"strings"

	"golang.org/x/sys/unix"

	"cherry-oj/judge-engine/internal/hostexec"
)

// putInput 用目录 FD 逐层创建并打开；已有目录也不能只因 Mkdirat 返回 EEXIST 就信任。
// O_EXCL 防止重复输入覆盖已交付文件，复制长度由请求声明限定。
func putInput(dir *os.File, in hostexec.Input, src io.Reader, uid, gid int) error {
	if !hostexec.ValidPath(in.Path) {
		return fmt.Errorf("illegal input path")
	}
	parts := strings.Split(in.Path, "/")
	cur, err := unix.Dup(int(dir.Fd()))
	if err != nil {
		return err
	}
	// cur 随逐层遍历更换；闭包要关闭最后一层，不能在 defer 注册时捕获初始整数 FD。
	defer func() { unix.Close(cur) }()
	for _, p := range parts[:len(parts)-1] {
		if err = unix.Mkdirat(cur, p, 0755); err != nil && !errors.Is(err, unix.EEXIST) {
			return err
		}
		next, e := unix.Openat2(cur, p, &unix.OpenHow{Flags: unix.O_DIRECTORY | unix.O_RDONLY | unix.O_CLOEXEC, Resolve: unix.RESOLVE_BENEATH | unix.RESOLVE_NO_SYMLINKS | unix.RESOLVE_NO_XDEV})
		if e != nil {
			return e
		}
		unix.Close(cur)
		cur = next
		if err = unix.Fchown(cur, uid, gid); err != nil {
			return err
		}
	}
	mode := uint64(0644)
	if in.Executable {
		mode = 0755
	}
	fd, err := unix.Openat2(cur, parts[len(parts)-1], &unix.OpenHow{Flags: unix.O_WRONLY | unix.O_CREAT | unix.O_EXCL | unix.O_NOFOLLOW | unix.O_CLOEXEC, Mode: mode, Resolve: unix.RESOLVE_BENEATH | unix.RESOLVE_NO_SYMLINKS | unix.RESOLVE_NO_XDEV})
	if err != nil {
		return err
	}
	f := os.NewFile(uintptr(fd), in.Path)
	_, err = io.CopyN(f, src, in.SizeBytes)
	if err == nil {
		err = unix.Fchown(fd, uid, gid)
	}
	return errors.Join(err, f.Close())
}
