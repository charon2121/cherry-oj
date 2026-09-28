//go:build linux && amd64

package execution

import (
	"cherry-oj/judge-engine/internal/hostexec"
	"errors"
	"fmt"
	"os"

	"golang.org/x/sys/unix"
)

// artifactSource 是已确认停止的工作区，Open 仍须验证链接、文件类型和挂载边界。
type artifactSource interface {
	Open(string) (*os.File, int64, error)
	Close() error
}

type workspaceDirectory struct{ File *ownedFile }

func (d workspaceDirectory) Close() error { return d.File.Close() }

// artifactSet 聚合已验证的句柄及长度，部分打开失败也保留已取得的句柄供关闭。
type artifactSet struct {
	outputs  []hostexec.Output
	files    []*ownedFile
	closed   bool
	closeErr error
}

func collectArtifacts(source artifactSource, names []string) (*artifactSet, error) {
	set := &artifactSet{}
	var total int64
	for _, name := range names {
		file, size, err := source.Open(name)
		// 声明的产物可未生成，runner 在实际请求 GetFile 时决定是否影响结果。
		if errors.Is(err, os.ErrNotExist) {
			continue
		}
		if err != nil {
			return set, fmt.Errorf("open artifact %s: %w", name, err)
		}
		if size < 0 || size > hostexec.MaxArtifactBytes-total {
			return set, errors.Join(fmt.Errorf("artifact total exceeds the limit: %s", name), file.Close())
		}
		total += size
		set.outputs = append(set.outputs, hostexec.Output{Path: name, SizeBytes: size})
		set.files = append(set.files, ownFile(file))
	}
	return set, nil
}

func (s *artifactSet) Close() error {
	if s == nil {
		return nil
	}
	if !s.closed {
		s.closed = true
		s.closeErr = closeFiles(s.files...)
		s.files = nil
	}
	return s.closeErr
}

// OpenOutput 必须在组清空后调用，避免用户进程边写边交付；调用者负责关闭返回文件。
// 路径解析限定在工作区内且禁止链接/跨挂载，同一 FD 校验后直接读取，避免重新打开的竞态。
func OpenOutput(dir *os.File, name string) (*os.File, int64, error) {
	if !hostexec.ValidPath(name) {
		return nil, 0, fmt.Errorf("illegal artifact path")
	}
	// O_NONBLOCK 防止 FIFO 等特殊文件在 fstat 拒绝它之前就把 isolator 阻塞在 open。
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

func (d workspaceDirectory) Open(name string) (*os.File, int64, error) {
	return OpenOutput(d.File.File, name)
}
