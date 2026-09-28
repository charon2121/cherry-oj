//go:build linux && amd64

package daemon

import (
	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/isolator/cgroup"
	"cherry-oj/judge-engine/isolator/execution"
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"os"
	"path/filepath"
	"sync"
	"time"

	"golang.org/x/sys/unix"
)

// 分阶段设置期限，避免慢请求占槽；恢复和探测独立于已接纳请求的会话期限。
const (
	requestHeaderTimeout = 5 * time.Second  // 开始处理连接后读取请求控制帧。
	deliveryTimeout      = 10 * time.Second // execution.Run 返回后单独刷新写端期限。
	recoveryTimeout      = 5 * time.Second  // 服务启动时回收旧的自有环境。
	managerCloseTimeout  = 5 * time.Second  // 停服时关闭资源组管理器。
)

// Serve 只接受配置中固定 UID，连接占用并发槽直到产物交付结束；超额立即拒绝。
// 调用者必须用 systemd 托管，SIGKILL 托底不能依赖 Go defer。
// logger 记录被拒绝的连接、协议错误与回收失败；nil 时使用 slog.Default。
func Serve(ctx context.Context, c Config, logger *slog.Logger) (result error) {
	if logger == nil {
		logger = slog.Default()
	}
	executable, err := checkInstallation(c)
	if err != nil {
		return err
	}
	s := &service{config: c, executable: executable, slots: availableSlots(c.Parallelism), fatal: make(chan error, 1), log: logger}
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
	log        *slog.Logger
}

// run 依次完成启动准备、开放 socket、接单；返回前关闭监听、资源组管理器并释放服务锁。
func (s *service) run(ctx context.Context) (result error) {
	lock, err := s.acquireLock()
	if err != nil {
		return err
	}
	defer func() { result = errors.Join(result, lock.Close()) }()
	manager, err := cgroup.Open(s.config.JobsDir)
	if err != nil {
		return err
	}
	s.manager = manager
	defer func() {
		cleanup, cancel := context.WithTimeout(context.Background(), managerCloseTimeout)
		defer cancel()
		result = errors.Join(result, manager.Close(cleanup))
	}()
	if err = s.prepare(ctx); err != nil {
		return err
	}
	if err = s.listen(); err != nil {
		return err
	}
	defer s.listener.Close()
	return s.accept(ctx)
}

// acquireLock 先持有服务锁再恢复遗留资源，避免两个 isolator 同时回收或分配同一组槽位身份。
func (s *service) acquireLock() (*os.File, error) {
	path := filepath.Join(s.config.StateDir, "lock")
	lock, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR|unix.O_NOFOLLOW, 0600)
	if err != nil {
		return nil, err
	}
	if err = securePath(path, false); err != nil {
		return nil, errors.Join(err, lock.Close())
	}
	if err = unix.Flock(int(lock.Fd()), unix.LOCK_EX|unix.LOCK_NB); err != nil {
		return nil, errors.Join(fmt.Errorf("isolator is already running: %w", err), lock.Close())
	}
	return lock, nil
}

// prepare 回收上次遗留的资源，再让每个槽位走一次真实执行链。
// 文件校验不能证明内核隔离能力；全部成功才开放 socket。
func (s *service) prepare(ctx context.Context) error {
	cleanup, cancel := context.WithTimeout(ctx, recoveryTimeout)
	err := recoverOwned(cleanup, s.config)
	cancel()
	if err != nil {
		return err
	}
	for slot := 0; slot < s.config.Parallelism; slot++ {
		if err := probeInstallation(ctx, s.config.forSlot(slot), s.manager, s.executable); err != nil {
			return fmt.Errorf("slot %d startup probe: %w", slot, err)
		}
	}
	return nil
}

// listen 创建 socket；文件权限与 SO_PEERCRED 双重约束，只有服务专用组可以建立连接。
func (s *service) listen() error {
	c := s.config
	listener, err := net.ListenUnix("unix", &net.UnixAddr{Name: c.SocketPath, Net: "unix"})
	if err != nil {
		return err
	}
	if err = os.Chown(c.SocketPath, 0, c.ServiceGID); err != nil {
		return errors.Join(err, listener.Close())
	}
	if err = os.Chmod(c.SocketPath, 0660); err != nil {
		return errors.Join(err, listener.Close())
	}
	s.listener = listener
	s.log.Info("isolator.listening", "socket", c.SocketPath, "slots", c.Parallelism)
	return nil
}

// accept 循环接单：认证对端、取槽位，每个连接在自己的 goroutine 里执行并交付。
// 任何连接报告回收失败都会停止接单，accept 等在途连接收尾后返回该错误。
func (s *service) accept(ctx context.Context) error {
	serveCtx, stop := context.WithCancel(ctx)
	defer stop()
	go func() { <-serveCtx.Done(); s.listener.Close() }()
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
		if err = checkPeer(conn, s.config.ServiceUID); err != nil {
			s.log.Warn("isolator.connection.rejected", "reason", "peer", "error", err)
			conn.Close()
			continue
		}
		var slot int
		select {
		case slot = <-s.slots:
		default:
			// 客户端只会看到 EOF；这里是区分「槽位已满」与其他断连的唯一记录。
			s.log.Warn("isolator.connection.rejected", "reason", "no-free-slot", "slots", s.config.Parallelism)
			conn.Close()
			continue
		}
		wg.Add(1)
		go func() {
			defer wg.Done()
			serveInSlot(conn, s.slots, slot, func() {
				// 回收失败会让槽位是否可复用变得不确定，先停接单再退出当前处理函数。
				if err := s.serveConn(serveCtx, conn, slot); err != nil {
					s.log.Error("isolator.reclaim.failed", "slot", slot, "error", err)
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
		return fmt.Errorf("isolator peer UID mismatch")
	}
	return nil
}

// serveConn 返回的错误仅用于通知 Serve 停服；普通执行失败通过 hostexec.Result 交付。
// 交付失败仍由 defer 关闭产物，serveInSlot 最后归还槽位并关闭连接。
func (s *service) serveConn(ctx context.Context, conn *net.UnixConn, slot int) (fatal error) {
	// 先限制请求头读取；校验通过后才设置后续会话期限（见 hostexec.SessionTimeout）。
	// 协议错误只关闭这条连接，不影响服务；请求内容不进日志。
	invalid := func(err error) error {
		s.log.Warn("isolator.request.invalid", "slot", slot, "error", err)
		return nil
	}
	if err := conn.SetDeadline(time.Now().Add(requestHeaderTimeout)); err != nil {
		return invalid(err)
	}
	var req hostexec.Request
	if err := hostexec.ReadFrame(conn, &req, hostexec.MaxFrameBytes); err != nil {
		return invalid(err)
	}
	if err := req.Validate(); err != nil {
		return invalid(err)
	}
	if err := conn.SetDeadline(time.Now().Add(hostexec.SessionTimeout)); err != nil {
		return invalid(err)
	}
	runCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	// execution 的输入读取阻塞在 socket；仅取消 ctx 不会唤醒它，必须推进读期限。
	interrupt := context.AfterFunc(runCtx, func() { conn.SetReadDeadline(time.Now()) })
	defer interrupt()
	delivery, fatal := execution.Run(runCtx, req, execution.Options{
		Environment: s.config.forSlot(slot).environment(s.executable), Source: conn, CancelInput: cancel,
		Groups: func(l cgroup.Limits) (execution.Group, error) { return s.manager.New(l) },
	})
	defer func() { fatal = errors.Join(fatal, delivery.Close()) }()
	if ctx.Err() != nil {
		return fatal
	}
	// 读取侧超时不妨碍给仍连接的调用方返回已取消/失败的资源事实。
	_ = conn.SetWriteDeadline(time.Now().Add(deliveryTimeout))
	// 交付失败通常是客户端已断开（请求被取消），执行本身已经回收，只记录不停服。
	if err := hostexec.WriteFrame(conn, delivery.Result, hostexec.MaxResultFrameBytes); err != nil {
		s.log.Info("isolator.delivery.interrupted", "slot", slot, "error", err)
		return fatal
	}
	if err := delivery.WriteFiles(conn); err != nil {
		s.log.Info("isolator.delivery.interrupted", "slot", slot, "error", err)
		return fatal
	}
	if err := delivery.Close(); err != nil {
		return errors.Join(fatal, err)
	}
	if fatal != nil {
		return fatal
	}
	// 资源句柄回收完成后才确认交付，客户端必须消费该尾帧。
	_ = hostexec.WriteFrame(conn, hostexec.Completion{Version: hostexec.Version, Complete: true}, hostexec.MaxCompletionFrameBytes)
	return fatal
}
