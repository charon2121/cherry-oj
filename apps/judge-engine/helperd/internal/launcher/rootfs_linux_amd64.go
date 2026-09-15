package launcher

import (
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"syscall"

	"golang.org/x/sys/unix"
)

// rootFilesystem 只存在于 P4 的锁定线程，拥有准备中的目录和 stdin。
// 挂载随 namespace 销毁；Close 只关闭本进程 FD，不负责宿主侧停组或卸载。
type rootFilesystem struct {
	stage               StageSpec
	root                string
	attempted, prepared bool
	dir, stdin          *os.File
	closeErr            error
}

func newRootFilesystem(stage StageSpec) *rootFilesystem {
	return &rootFilesystem{stage: stage, root: stage.MountPoint}
}
func (f *rootFilesystem) at(path string) string { return filepath.Join(f.root, path) }

func (f *rootFilesystem) Prepare(source io.Reader) (err error) {
	if f.attempted {
		return fmt.Errorf("rootfs can only be prepared once")
	}
	f.attempted = true
	defer func() {
		if !f.prepared {
			err = errors.Join(err, f.Close())
		}
	}()
	if err = f.mountRoot(); err != nil {
		return err
	}
	if err = f.mountWorkspace(); err != nil {
		return err
	}
	if err = f.mountSystemViews(); err != nil {
		return err
	}
	if err = f.enterRoot(); err != nil {
		return err
	}
	if err = f.loadInputs(source); err != nil {
		return err
	}
	f.prepared = true
	return nil
}

// TakeInputs 把 FD 交给 initSession；后续 Close 不会再关闭已移交的句柄。
func (f *rootFilesystem) TakeInputs() (*os.File, *os.File) {
	if !f.prepared {
		return nil, nil
	}
	dir, stdin := f.dir, f.stdin
	f.dir, f.stdin = nil, nil
	return dir, stdin
}
func (f *rootFilesystem) Close() error {
	if f.dir != nil {
		f.closeErr = errors.Join(f.closeErr, f.dir.Close())
		f.dir = nil
	}
	if f.stdin != nil {
		f.closeErr = errors.Join(f.closeErr, f.stdin.Close())
		f.stdin = nil
	}
	return f.closeErr
}

func (f *rootFilesystem) mountRoot() error {
	if os.Getpid() != 1 || os.Geteuid() != 0 {
		return fmt.Errorf("init must be root PID 1 in a new PID namespace")
	}
	if err := unix.Mount("", "/", "", unix.MS_REC|unix.MS_PRIVATE, ""); err != nil {
		return err
	}
	if err := unix.Sethostname([]byte("cherry-sandbox")); err != nil {
		return err
	}
	root := f.root
	if err := unix.Mount(f.stage.RootFS, root, "", unix.MS_BIND, ""); err != nil {
		return err
	}
	at := f.at
	// 目标均由可信 rootfs 制作器预建，禁止跟随 symlink 到非预期挂载点。
	for _, p := range []string{"work", "tmp", "proc", "dev", ".oldroot", ".sandbox", ".sandbox/launcher"} {
		st, err := os.Lstat(at(p))
		if err != nil {
			return err
		}
		if st.Mode()&os.ModeSymlink != 0 {
			return fmt.Errorf("rootfs mount point must not be a link")
		}
	}
	if err := unix.Mount(f.stage.Executable, at(".sandbox/launcher"), "", unix.MS_BIND, ""); err != nil {
		return err
	}
	if err := unix.Mount("", at(".sandbox/launcher"), "", unix.MS_BIND|unix.MS_REMOUNT|unix.MS_RDONLY|unix.MS_NOSUID|unix.MS_NODEV, ""); err != nil {
		return err
	}
	if err := unix.Mount("", root, "", unix.MS_BIND|unix.MS_REMOUNT|unix.MS_RDONLY|unix.MS_NOSUID|unix.MS_NODEV, ""); err != nil {
		return err
	}

	return nil
}

func (f *rootFilesystem) mountWorkspace() error {
	at := f.at
	flags := uintptr(unix.MS_NOSUID | unix.MS_NODEV)
	opts := fmt.Sprintf("size=%d,nr_inodes=%d,mode=0755,uid=%d,gid=%d", f.stage.WorkspaceBytes, f.stage.WorkspaceInodes, f.stage.PayloadUID, f.stage.PayloadGID)
	if err := unix.Mount("tmpfs", at("work"), "tmpfs", flags, opts); err != nil {
		return err
	}
	if err := os.Mkdir(at("work/.tmp"), 01777); err != nil {
		return err
	}
	if err := unix.Chmod(at("work/.tmp"), 01777); err != nil {
		return err
	}
	// /tmp 与 /work 使用同一 tmpfs，临时文件仍计入同一容量和 inode 预算。
	if err := unix.Mount(at("work/.tmp"), at("tmp"), "", unix.MS_BIND, ""); err != nil {
		return err
	}

	return nil
}

func (f *rootFilesystem) mountSystemViews() error {
	flags := uintptr(unix.MS_NOSUID | unix.MS_NODEV)
	at := f.at
	if err := unix.Mount("proc", at("proc"), "proc", flags|unix.MS_NOEXEC|unix.MS_RDONLY, "hidepid=2,subset=pid"); err != nil {
		return err
	}
	if err := unix.Mount("tmpfs", at("dev"), "tmpfs", unix.MS_NOSUID|unix.MS_NOEXEC, "size=4096,nr_inodes=8,mode=0755"); err != nil {
		return err
	}
	// 使用 Linux 内存设备的固定主/次设备号重建最小 /dev，不暴露宿主设备树。
	for _, dev := range []struct {
		name  string
		minor uint32
	}{{"null", 3}, {"zero", 5}, {"random", 8}, {"urandom", 9}} {
		if err := unix.Mknod(at("dev/"+dev.name), unix.S_IFCHR|0666, int(unix.Mkdev(1, dev.minor))); err != nil {
			return err
		}
		if err := unix.Chmod(at("dev/"+dev.name), 0666); err != nil {
			return err
		}
	}
	if err := unix.Mount("", at("dev"), "", unix.MS_REMOUNT|unix.MS_RDONLY|unix.MS_NOSUID|unix.MS_NOEXEC, ""); err != nil {
		return err
	}

	return nil
}

func (f *rootFilesystem) enterRoot() error {
	root, at := f.root, f.at
	if err := syscall.PivotRoot(root, at(".oldroot")); err != nil {
		return err
	}
	// pivot_root 后旧工作目录仍可能引用旧根；先切到新根，再断开旧根挂载。
	if err := os.Chdir("/"); err != nil {
		return err
	}
	if err := unix.Unmount("/.oldroot", unix.MNT_DETACH); err != nil {
		return err
	}

	return nil
}

func (f *rootFilesystem) loadInputs(source io.Reader) error {
	dir, err := os.Open("/work")
	if err != nil {
		return err
	}
	f.dir = dir
	for _, input := range f.stage.Request.Inputs {
		if err = putInput(dir, input, source, f.stage.PayloadUID, f.stage.PayloadGID); err != nil {
			return err
		}
	}
	// stdin 在本组计量下写入工作区，重开为只读并 unlink 后才交给 payload；
	// 用户命令能读取 FD 0，却不能通过文件名修改这份输入。
	path := "/work/.stdin"
	stdinWriter, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	_, copyErr := io.CopyN(stdinWriter, source, f.stage.Request.StdinBytes)
	closeErr := stdinWriter.Close()
	if copyErr != nil {
		return copyErr
	}
	if closeErr != nil {
		return closeErr
	}
	stdin, err := os.Open(path)
	if err != nil {
		return err
	}
	f.stdin = stdin
	if err = os.Remove(path); err != nil {
		return err
	}
	if err = os.Chdir("/work"); err != nil {
		return err
	}
	return nil
}
