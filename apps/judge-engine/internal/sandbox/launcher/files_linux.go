package launcher

import (
	"errors"
	"fmt"
	"io"
	"os"
	"strings"

	"golang.org/x/sys/unix"
)

// OpenOutput 在组清空后调用；同一 FD 完成类型/链接数校验和读取，不重新按字符串打开。
func OpenOutput(dir *os.File, name string) (*os.File, int64, error) {
	if !ValidPath(name) {
		return nil, 0, fmt.Errorf("非法产物路径")
	}
	fd, err := unix.Openat2(int(dir.Fd()), name, &unix.OpenHow{Flags: unix.O_RDONLY | unix.O_CLOEXEC | unix.O_NONBLOCK, Resolve: unix.RESOLVE_BENEATH | unix.RESOLVE_NO_SYMLINKS | unix.RESOLVE_NO_MAGICLINKS | unix.RESOLVE_NO_XDEV})
	if err != nil {
		return nil, 0, err
	}
	f := os.NewFile(uintptr(fd), name)
	var st unix.Stat_t
	if err = unix.Fstat(fd, &st); err != nil {
		f.Close()
		return nil, 0, err
	}
	if st.Mode&unix.S_IFMT != unix.S_IFREG || st.Nlink != 1 || st.Size < 0 || st.Size > MaxArtifactBytes {
		f.Close()
		return nil, 0, fmt.Errorf("产物不是唯一链接的有界普通文件")
	}
	return f, st.Size, nil
}
func putInput(dir *os.File, in Input, src io.Reader, uid, gid int) error {
	if !ValidPath(in.Path) {
		return fmt.Errorf("非法输入路径")
	}
	parts := strings.Split(in.Path, "/")
	cur, err := unix.Dup(int(dir.Fd()))
	if err != nil {
		return err
	}
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
