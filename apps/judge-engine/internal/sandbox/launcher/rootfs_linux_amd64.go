package launcher

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"syscall"

	"golang.org/x/sys/unix"
)

// prepareRoot 只在带新 mount/pid namespace 的 PID 1 内调用。
// 所有挂载均为 private；不在 helper 的宿主 mount namespace 做挂载。
func prepareRoot(s StageSpec, source io.Reader) (*os.File, *os.File, error) {
	if os.Getpid() != 1 || os.Geteuid() != 0 {
		return nil, nil, fmt.Errorf("init 必须为新 PID namespace 中的 root PID 1")
	}
	if err := unix.Mount("", "/", "", unix.MS_REC|unix.MS_PRIVATE, ""); err != nil {
		return nil, nil, err
	}
	if err := unix.Sethostname([]byte("cherry-sandbox")); err != nil {
		return nil, nil, err
	}
	root := s.MountPoint
	if err := unix.Mount(s.RootFS, root, "", unix.MS_BIND, ""); err != nil {
		return nil, nil, err
	}
	at := func(p string) string { return filepath.Join(root, p) }
	// 目标均由可信 rootfs 制作器预建，禁止跟随 symlink 到非预期挂载点。
	for _, p := range []string{"work", "tmp", "proc", "dev", ".oldroot", ".sandbox", ".sandbox/launcher"} {
		st, err := os.Lstat(at(p))
		if err != nil {
			return nil, nil, err
		}
		if st.Mode()&os.ModeSymlink != 0 {
			return nil, nil, fmt.Errorf("rootfs 挂载点不能为链接")
		}
	}
	if err := unix.Mount(s.Executable, at(".sandbox/launcher"), "", unix.MS_BIND, ""); err != nil {
		return nil, nil, err
	}
	if err := unix.Mount("", at(".sandbox/launcher"), "", unix.MS_BIND|unix.MS_REMOUNT|unix.MS_RDONLY|unix.MS_NOSUID|unix.MS_NODEV, ""); err != nil {
		return nil, nil, err
	}
	if err := unix.Mount("", root, "", unix.MS_BIND|unix.MS_REMOUNT|unix.MS_RDONLY|unix.MS_NOSUID|unix.MS_NODEV, ""); err != nil {
		return nil, nil, err
	}
	flags := uintptr(unix.MS_NOSUID | unix.MS_NODEV)
	opts := fmt.Sprintf("size=%d,nr_inodes=%d,mode=0755,uid=%d,gid=%d", s.WorkspaceBytes, s.WorkspaceInodes, s.PayloadUID, s.PayloadGID)
	if err := unix.Mount("tmpfs", at("work"), "tmpfs", flags, opts); err != nil {
		return nil, nil, err
	}
	if err := os.Mkdir(at("work/.tmp"), 01777); err != nil {
		return nil, nil, err
	}
	if err := unix.Chmod(at("work/.tmp"), 01777); err != nil {
		return nil, nil, err
	}
	if err := unix.Mount(at("work/.tmp"), at("tmp"), "", unix.MS_BIND, ""); err != nil {
		return nil, nil, err
	}
	if err := unix.Mount("proc", at("proc"), "proc", flags|unix.MS_NOEXEC|unix.MS_RDONLY, "hidepid=2,subset=pid"); err != nil {
		return nil, nil, err
	}
	if err := unix.Mount("tmpfs", at("dev"), "tmpfs", unix.MS_NOSUID|unix.MS_NOEXEC, "size=4096,nr_inodes=8,mode=0755"); err != nil {
		return nil, nil, err
	}
	for _, dev := range []struct {
		name  string
		minor uint32
	}{{"null", 3}, {"zero", 5}, {"random", 8}, {"urandom", 9}} {
		if err := unix.Mknod(at("dev/"+dev.name), unix.S_IFCHR|0666, int(unix.Mkdev(1, dev.minor))); err != nil {
			return nil, nil, err
		}
		if err := unix.Chmod(at("dev/"+dev.name), 0666); err != nil {
			return nil, nil, err
		}
	}
	if err := unix.Mount("", at("dev"), "", unix.MS_REMOUNT|unix.MS_RDONLY|unix.MS_NOSUID|unix.MS_NOEXEC, ""); err != nil {
		return nil, nil, err
	}
	if err := syscall.PivotRoot(root, at(".oldroot")); err != nil {
		return nil, nil, err
	}
	if err := os.Chdir("/"); err != nil {
		return nil, nil, err
	}
	if err := unix.Unmount("/.oldroot", unix.MNT_DETACH); err != nil {
		return nil, nil, err
	}
	dir, err := os.Open("/work")
	if err != nil {
		return nil, nil, err
	}
	fail := func(err error) (*os.File, *os.File, error) { dir.Close(); return nil, nil, err }
	for _, f := range s.Request.Inputs {
		if err = putInput(dir, f, source, s.PayloadUID, s.PayloadGID); err != nil {
			return fail(err)
		}
	}
	// stdin 从被计量的 init 写入匿名临时文件，payload 仅得到只读 FD 0。
	path := "/work/.stdin"
	f, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if err != nil {
		return fail(err)
	}
	_, copyErr := io.CopyN(f, source, s.Request.StdinBytes)
	closeErr := f.Close()
	if copyErr != nil {
		return fail(copyErr)
	}
	if closeErr != nil {
		return fail(closeErr)
	}
	stdin, err := os.Open(path)
	if err != nil {
		return fail(err)
	}
	if err = os.Remove(path); err != nil {
		stdin.Close()
		return fail(err)
	}
	if err = os.Chdir("/work"); err != nil {
		stdin.Close()
		return fail(err)
	}
	return dir, stdin, nil
}
