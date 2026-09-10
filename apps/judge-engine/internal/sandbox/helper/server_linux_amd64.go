package helper

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"runtime/debug"
	"sync"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/cgroup"
	"cherry-oj/judge-engine/internal/sandbox/launcher"
	"golang.org/x/sys/unix"
)

// Serve 只接受配置中固定 UID，连接占用并发槽直到产物交付结束；超额立即拒绝。
// 调用者必须用 systemd 托管，SIGKILL 托底不能依赖 Go defer。
func Serve(ctx context.Context, c Config) (result error) {
	executable, err := checkInstallation(c)
	if err != nil {
		return err
	}
	lock, err := os.OpenFile(filepath.Join(c.StateDir, "lock"), os.O_CREATE|os.O_RDWR|unix.O_NOFOLLOW, 0600)
	if err != nil {
		return err
	}
	defer func() { result = errors.Join(result, lock.Close()) }()
	if err = securePath(filepath.Join(c.StateDir, "lock"), false); err != nil {
		return err
	}
	if err = unix.Flock(int(lock.Fd()), unix.LOCK_EX|unix.LOCK_NB); err != nil {
		return fmt.Errorf("helper 已运行: %w", err)
	}
	manager, err := cgroup.Open(c.JobsDir)
	if err != nil {
		return err
	}
	defer func() {
		cleanup, cancel := context.WithTimeout(context.Background(), cleanupTimeout)
		defer cancel()
		result = errors.Join(result, manager.Close(cleanup))
	}()
	cleanup, cancel := context.WithTimeout(ctx, 5*time.Second)
	err = recoverOwned(cleanup, c)
	cancel()
	if err != nil {
		return err
	}
	for slot := 0; slot < c.Parallelism; slot++ {
		if err := probeInstallation(ctx, c.forSlot(slot), manager, executable); err != nil {
			return fmt.Errorf("槽位%d启动探测: %w", slot, err)
		}
	}
	listener, err := net.ListenUnix("unix", &net.UnixAddr{Name: c.SocketPath, Net: "unix"})
	if err != nil {
		return err
	}
	defer listener.Close()
	// 文件权限与 SO_PEERCRED 双重约束，只有服务专用组可以建立连接。
	if err = os.Chown(c.SocketPath, 0, c.ServiceGID); err != nil {
		return err
	}
	if err = os.Chmod(c.SocketPath, 0660); err != nil {
		return err
	}
	serveCtx, stop := context.WithCancel(ctx)
	defer stop()
	go func() { <-serveCtx.Done(); listener.Close() }()
	var wg sync.WaitGroup
	defer func() { stop(); wg.Wait() }()
	slots := availableSlots(c.Parallelism)
	fatal := make(chan error, 1)
	for {
		conn, err := listener.AcceptUnix()
		if err != nil {
			select {
			case e := <-fatal:
				return e
			default:
			}
			if serveCtx.Err() != nil {
				return nil
			}
			return err
		}
		if err = checkPeer(conn, c.ServiceUID); err != nil {
			conn.Close()
			continue
		}
		var slot int
		select {
		case slot = <-slots:
		default:
			conn.Close()
			continue
		}
		wg.Add(1)
		go func() {
			defer wg.Done()
			serveInSlot(conn, slots, slot, func() {
				if err := serveConn(serveCtx, conn, c.forSlot(slot), manager, executable); err != nil {
					select {
					case fatal <- err:
					default:
					}
					stop()
				}
			})
		}()
	}
}

// serveInSlot 接管已取得的槽位和连接。serve 包含交付、资源回收和 fatal 停服处理；
// 只有它完整返回后才归还槽位，最后的 socket EOF 让客户端观察到这一顺序。
func serveInSlot(conn io.Closer, slots chan int, slot int, serve func()) {
	defer conn.Close()
	defer func() { slots <- slot }()
	serve()
}

func checkPeer(c *net.UnixConn, uid int) error {
	raw, err := c.SyscallConn()
	if err != nil {
		return err
	}
	var cred *unix.Ucred
	var inner error
	if err = raw.Control(func(fd uintptr) { cred, inner = unix.GetsockoptUcred(int(fd), unix.SOL_SOCKET, unix.SO_PEERCRED) }); err != nil {
		return err
	}
	if inner != nil {
		return inner
	}
	if cred == nil || cred.Uid != uint32(uid) {
		return fmt.Errorf("helper 对端 UID 不匹配")
	}
	return nil
}
func serveConn(ctx context.Context, conn *net.UnixConn, c Config, m *cgroup.Manager, executable string) (fatal error) {
	// 总传输期限覆盖 header、输入、执行、清理和产物，不允许客户端无限占槽。
	if err := conn.SetDeadline(time.Now().Add(5 * time.Second)); err != nil {
		return nil
	}
	var req launcher.Request
	if err := launcher.ReadFrame(conn, &req, launcher.MaxFrameBytes); err != nil {
		return nil
	}
	if req.Validate() != nil {
		return nil
	}
	if err := conn.SetDeadline(time.Now().Add(150 * time.Second)); err != nil {
		return nil
	}
	runCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	interrupt := context.AfterFunc(runCtx, func() { conn.SetReadDeadline(time.Now()) })
	defer interrupt()
	res, fatal := execute(runCtx, req, conn, c, func(l cgroup.Limits) (executionGroup, error) { return m.New(l) }, executable, cancel)
	defer func() { fatal = errors.Join(fatal, res.Close()) }()
	if ctx.Err() != nil {
		return fatal
	}
	// 读取侧超时不妨碍给仍连接的调用方返回已取消/失败的资源事实。
	_ = conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
	if err := launcher.WriteFrame(conn, res.Result, 4<<20); err != nil {
		return fatal
	}
	if err := res.WriteFiles(conn); err != nil {
		return fatal
	}
	if err := res.Close(); err != nil {
		return errors.Join(fatal, err)
	}
	if fatal != nil {
		return fatal
	}
	// 资源句柄回收完成后才确认交付，客户端必须消费该尾帧。
	_ = launcher.WriteFrame(conn, Completion{Version: launcher.Version, Complete: true}, 1024)
	return fatal
}

// checkInstallation 只读校验安装可信性；成功不代表已经通过真实隔离启动。
func checkInstallation(c Config) (string, error) {
	if err := c.Validate(); err != nil {
		return "", err
	}
	info, ok := debug.ReadBuildInfo()
	pure := false
	if ok {
		for _, setting := range info.Settings {
			if setting.Key == "CGO_ENABLED" && setting.Value == "0" {
				pure = true
			}
		}
	}
	if !pure {
		return "", fmt.Errorf("helper 必须使用 CGO_ENABLED=0 构建")
	}
	if os.Geteuid() != 0 {
		return "", fmt.Errorf("helper 必须由 root 托管")
	}
	for _, p := range []string{c.StateDir, c.JobsDir} {
		if err := securePath(p, true); err != nil {
			return "", err
		}
	}
	if err := verifyRoot(c); err != nil {
		return "", err
	}
	executable, err := os.Executable()
	if err != nil {
		return "", err
	}
	if err = securePath(executable, false); err != nil {
		return "", err
	}
	binaryInfo, err := os.Stat(executable)
	if err != nil {
		return "", err
	}
	if !binaryInfo.Mode().IsRegular() || binaryInfo.Mode()&(os.ModeSetuid|os.ModeSetgid) != 0 {
		return "", fmt.Errorf("helper 必须为不带 setuid/setgid 的普通可执行文件")
	}

	return executable, nil
}

// probeInstallation 在监听前通过真实执行链验证部署；不使用宿主 true。
func probeInstallation(ctx context.Context, c Config, manager *cgroup.Manager, executable string) error {
	probeCtx, probeCancel := context.WithTimeout(ctx, 5*time.Second)
	defer probeCancel()
	probeR, probeW := io.Pipe()
	closeInput := sync.OnceValue(probeR.Close)
	probeInputCancel := func() { probeCancel(); closeInput() }
	probe := launcher.Request{Version: launcher.Version, Command: []string{"true"}, Limits: contract.ExplicitLimits(contract.Limits{CPUNs: 2_000_000_000, ClockNs: 5_000_000_000, MemoryBytes: 128 << 20, MaxProcesses: 64, StdoutMaxBytes: 1024, StderrMaxBytes: 1024})}
	facts, err := execute(probeCtx, probe, probeR, c, func(l cgroup.Limits) (executionGroup, error) { return manager.New(l) }, executable, probeInputCancel)
	probeInputCancel()
	if err = errors.Join(err, closeInput(), probeW.Close(), facts.Close()); err != nil {
		return err
	}
	if facts.Reason != "" || facts.ExitCode != 0 || facts.Signal != 0 || facts.Usage.CPUNs <= 0 || facts.Usage.MemoryBytes <= 0 || len(facts.Stdout) != 0 || len(facts.Stderr) != 0 {
		return fmt.Errorf("隔离启动能力冒烟失败: reason=%s error=%s stderr=%q", facts.Reason, facts.Error, facts.Stderr)
	}
	return nil
}
