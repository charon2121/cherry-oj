//go:build linux && amd64

package execution

import (
	"fmt"
	"os"

	"golang.org/x/sys/unix"

	"cherry-oj/judge-engine/internal/hostexec"
)

// OpenOutput 必须在组清空后调用，避免用户进程边写边交付；调用者负责关闭返回文件。
// 路径解析限定在工作区内且禁止链接/跨挂载，同一 FD 校验后直接读取，避免重新打开的竞态。
func OpenOutput(dir *os.File, name string) (*os.File, int64, error) {
	if !hostexec.ValidPath(name) {
		return nil, 0, fmt.Errorf("illegal artifact path")
	}
	// O_NONBLOCK 防止 FIFO 等特殊文件在 fstat 拒绝它之前就把 helper 阻塞在 open。
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
	if st.Mode&unix.S_IFMT != unix.S_IFREG || st.Nlink != 1 || st.Size < 0 || st.Size > hostexec.MaxArtifactBytes {
		f.Close()
		return nil, 0, fmt.Errorf("artifact is not a bounded regular file with exactly one link")
	}
	return f, st.Size, nil
}
