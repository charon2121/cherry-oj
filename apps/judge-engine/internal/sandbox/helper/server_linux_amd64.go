package helper

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"sync"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/internal/sandbox/cgroup"

	"golang.org/x/sys/unix"
)

// 分阶段设置期限，避免慢请求占槽；恢复和探测独立于已接纳请求的会话期限。
const (
	requestHeaderTimeout = 5 * time.Second  // 开始处理连接后读取请求控制帧。
	deliveryTimeout      = 10 * time.Second // execution.Run 返回后单独刷新写端期限。
	recoveryTimeout      = 5 * time.Second  // 服务启动时回收旧的自有环境。
)

// Serve 只接受配置中固定 UID，连接占用并发槽直到产物交付结束；超额立即拒绝。
// 调用者必须用 systemd 托管，SIGKILL 托底不能依赖 Go defer。
func Serve(ctx context.Context, c Config) (result error) {
	executable, err := checkInstallation(c)
	if err != nil {
		return err
	}
	s := &service{config: c, executable: executable, slots: availableSlots(c.Parallelism), fatal: make(chan error, 1)}
	return s.run(ctx)
}

// service 拥有监听、资源组管理器和接纳名额；单次执行的 FD 与状态不进入服务。
type service struct {
	config     Config
	executable string
	manager    *cgroup.Manager
	listener   *net.UnixListener
	slots      chan int
	fatal      chan error
}

func (s *service) run(ctx context.Context) (result error) {
	c := s.config
	// 先持有服务锁再恢复遗留资源，避免两个 helper 同时回收或分配同一组槽位身份。
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
	s.manager = manager
	defer func() {
		cleanup, cancel := context.WithTimeout(context.Background(), cleanupTimeout)
		defer cancel()
		result = errors.Join(result, manager.Close(cleanup))
	}()
	cleanup, cancel := context.WithTimeout(ctx, recoveryTimeout)
	err = recoverOwned(cleanup, c)
	cancel()
	if err != nil {
		return err
	}
	// 文件校验不能证明内核隔离能力；每组身份都走真实执行链，全部成功才开放 socket。
	for slot := 0; slot < c.Parallelism; slot++ {
		if err := probeInstallation(ctx, c.forSlot(slot), s.manager, s.executable); err != nil {
			return fmt.Errorf("槽位%d启动探测: %w", slot, err)
		}
	}
	listener, err := net.ListenUnix("unix", &net.UnixAddr{Name: c.SocketPath, Net: "unix"})
	if err != nil {
		return err
	}
	s.listener = listener
	defer s.listener.Close()
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
	for {
		conn, err := s.listener.AcceptUnix()
		if err != nil {
			select {
			case e := <-s.fatal:
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
		case slot = <-s.slots:
		default:
			conn.Close()
			continue
		}
		wg.Add(1)
		go func() {
			defer wg.Done()
			serveInSlot(conn, s.slots, slot, func() {
				// 回收失败会让槽位是否可复用变得不确定，先停接单再退出当前处理函数。
				if err := s.serveConn(serveCtx, conn, slot); err != nil {
					select {
					case s.fatal <- err:
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

// serveConn 返回的错误仅用于通知 Serve 停服；普通执行失败通过 hostexec.Result 交付。
// 交付失败仍由 defer 关闭产物，serveInSlot 最后归还槽位并关闭连接。
func (s *service) serveConn(ctx context.Context, conn *net.UnixConn, slot int) (fatal error) {
	// 先限制请求头读取；校验通过后才设置后续会话期限（见 hostexec.SessionTimeout）。
	if err := conn.SetDeadline(time.Now().Add(requestHeaderTimeout)); err != nil {
		return nil
	}
	var req hostexec.Request
	if err := hostexec.ReadFrame(conn, &req, hostexec.MaxFrameBytes); err != nil {
		return nil
	}
	if req.Validate() != nil {
		return nil
	}
	if err := conn.SetDeadline(time.Now().Add(hostexec.SessionTimeout)); err != nil {
		return nil
	}
	runCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	// execution 的输入读取阻塞在 socket；仅取消 ctx 不会唤醒它，必须推进读期限。
	interrupt := context.AfterFunc(runCtx, func() { conn.SetReadDeadline(time.Now()) })
	defer interrupt()
	run := newExecution(req, executionOptions{
		config: s.config.forSlot(slot), source: conn, executable: s.executable, cancelInput: cancel,
		groups: func(l cgroup.Limits) (executionGroup, error) { return s.manager.New(l) },
	})
	res, fatal := run.Run(runCtx)
	defer func() { fatal = errors.Join(fatal, res.Close()) }()
	if ctx.Err() != nil {
		return fatal
	}
	// 读取侧超时不妨碍给仍连接的调用方返回已取消/失败的资源事实。
	_ = conn.SetWriteDeadline(time.Now().Add(deliveryTimeout))
	if err := hostexec.WriteFrame(conn, res.Result, hostexec.MaxResultFrameBytes); err != nil {
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
	_ = hostexec.WriteFrame(conn, hostexec.Completion{Version: hostexec.Version, Complete: true}, hostexec.MaxCompletionFrameBytes)
	return fatal
}
