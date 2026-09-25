package initproc

import (
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"runtime"
	"syscall"

	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/isolator/startup"
)

// init 接受的工作区硬边界；helper 的实际工作区策略仍由 helper 决定。
const (
	maxWorkspaceBytes  = 128 << 20
	maxWorkspaceInodes = 4096
)

// initSession 的资源仅由主 goroutine 关闭；监测 goroutine 只读，发现断连即退出 PID 1。
// PID 1 退出时内核终止整个 namespace，外部 helper 仍须 Stop/Wait 并验证组清空。
type initSession struct {
	control                *os.File
	files                  map[*os.File]struct{}
	phase                  string
	child                  *exec.Cmd
	parentSync, execErrors *os.File
}

func (s *initSession) own(files ...*os.File) {
	for _, f := range files {
		if f != nil {
			s.files[f] = struct{}{}
		}
	}
}
func (s *initSession) close(files ...*os.File) error {
	var result error
	for _, f := range files {
		if _, ok := s.files[f]; ok {
			delete(s.files, f)
			result = errors.Join(result, f.Close())
		}
	}
	return result
}
func (s *initSession) closeAll() error {
	var result error
	for f := range s.files {
		result = errors.Join(result, s.close(f))
	}
	return result
}

// Run 是 P4 的入口：在 namespace PID 1 内协调可信启动，永不返回。
// main 必须在读取配置、启动服务或建立任何 goroutine 之前调用它；主线程的挂载与降权状态
// 必须连续，不能返回服务循环，也不能在中途迁移到别的 OS 线程。
func Run() {
	runtime.LockOSThread()
	control := os.NewFile(startup.InitControlFD, "helper-control")
	s := &initSession{control: control, files: make(map[*os.File]struct{}), phase: "configuration"}
	err := s.run()
	// 先保留原始阶段/errno，再关闭剩余 FD；此处不再启动任何用户代码。
	err = errors.Join(err, s.closeAll())
	if err != nil {
		var errno syscall.Errno
		errors.As(err, &errno)
		report := startup.Event{Version: hostexec.Version, Kind: "error", Phase: s.phase, Errno: uint32(errno)}
		if b, e := marshalEvent(report); e == nil {
			_, _ = control.Write(b)
		}
	}
	// 控制通道写失败也不能返回普通服务循环。
	os.Exit(startup.FailureExitCode)
}
func (s *initSession) run() error {
	src := os.NewFile(startup.InitInputFD, "stage-input")
	life := os.NewFile(startup.InitLivenessFD, "helper-liveness")
	s.own(src)
	// life 与 control 存活到 PID 1 退出，由内核关闭；主动关闭会触发断连监测。
	go exitOnDisconnect(life)
	var stage startup.StageSpec
	if err := hostexec.ReadFrame(src, &stage, hostexec.MaxFrameBytes); err != nil {
		return err
	}
	if err := stage.Request.Validate(); err != nil {
		return err
	}
	if stage.PayloadUID <= 0 || stage.InitUID <= 0 || stage.PayloadUID == stage.InitUID || stage.PayloadGID <= 0 || stage.InitGID <= 0 || stage.PayloadGID == stage.InitGID || stage.WorkspaceBytes <= 0 || stage.WorkspaceBytes > maxWorkspaceBytes || stage.WorkspaceInodes <= 0 || stage.WorkspaceInodes > maxWorkspaceInodes {
		return fmt.Errorf("invalid trusted startup configuration")
	}
	s.phase = "rootfs-input"
	filesystem := newRootFilesystem(stage)
	if err := filesystem.Prepare(src); err != nil {
		return err
	}
	dir, stdin := filesystem.TakeInputs()
	s.own(dir, stdin)
	var err error
	if err = s.close(src); err != nil {
		return err
	}
	// helper 需要持有目录 FD，才能在 namespace 停止后读取同一个工作区；
	// 传递宿主路径既不能定位该 tmpfs，也会扩大路径解析的权限边界。
	if err = startup.SendEvent(s.control, startup.Event{Kind: "workspace"}, dir); err != nil {
		return err
	}
	if err = s.close(dir); err != nil {
		return err
	}
	if err = s.startPayload(stage, stdin); err != nil {
		return err
	}
	if err = s.releasePayload(stage); err != nil {
		return err
	}
	if err = s.reportExit(); err != nil {
		return err
	}
	s.phase = "reported"
	return reapDescendants()
}

// helper 消失或主动关断时退出 PID 1，让 namespace 中的进程失去存活条件；
// 外部 helper 存活时仍必须 Stop/Wait，不能把该兜底当作已完成回收。
func exitOnDisconnect(f *os.File) {
	var b [1]byte
	_, _ = f.Read(b[:])
	os.Exit(startup.FailureExitCode)
}

func (s *initSession) reportExit() error {
	s.phase = "payload-wait"
	waitErr := s.child.Wait()
	var exit *exec.ExitError
	if waitErr != nil && !errors.As(waitErr, &exit) {
		return waitErr
	}
	report := startup.Event{Version: hostexec.Version, Kind: "exit", ExitCode: s.child.ProcessState.ExitCode()}
	if ws, ok := s.child.ProcessState.Sys().(syscall.WaitStatus); ok && ws.Signaled() {
		report.Signal = int(ws.Signal())
	}
	// 最终 exec 的写端带 CLOEXEC；正常关闭得到 EOF，残缺记录也要作为启动异常，
	// 不能把无法解码的 errno 静默当成没有错误。
	var record [startup.ExecFailureRecordBytes]byte
	n, readErr := io.ReadFull(s.execErrors, record[:])
	report.ExecFailed = n != 0 || readErr != io.EOF
	if n == len(record) && readErr == nil {
		report.ExecStage = record[startup.ExecFailureStageOffset]
		report.ExecErrno = binary.LittleEndian.Uint32(record[startup.ExecFailureErrnoOffset:])
	}
	if err := s.close(s.execErrors); err != nil {
		return err
	}
	b, err := marshalEvent(report)
	if err != nil {
		return err
	}
	_, err = s.control.Write(b)
	return err
}
func reapDescendants() error {
	// 不与 cmd.Wait 争抢主进程；报告后等 helper 停止整组，期间回收孤儿。
	// 无子进程时仍不能主动退出 PID 1，否则 helper 可能先观察到 init 丢失而非退出事件。
	for {
		var ws syscall.WaitStatus
		_, err := syscall.Wait4(-1, &ws, 0, nil)
		switch {
		case err == nil, errors.Is(err, syscall.EINTR):
			continue
		case errors.Is(err, syscall.ECHILD):
			select {} // 存活管道仍在监测 helper。
		default:
			return fmt.Errorf("reclaim namespace descendants: %w", err)
		}
	}
}
