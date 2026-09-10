package launcher

import (
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"runtime"
	"syscall"

	"cherry-oj/judge-engine/internal/sandbox/policy"
)

// Dispatch 必须在 main 读取配置、启动服务或建立 goroutine 之前调用。
// 隐藏模式没有 setuid 位，只接收已继承的匿名 FD；请求不能选择它。
func Dispatch() bool {
	if len(os.Args) != 2 {
		return false
	}
	switch os.Args[1] {
	case "--isolated-init":
		runInit()
		os.Exit(125)
	case "--isolated-exec":
		config := os.NewFile(3, "exec-config")
		var s ExecSpec
		err := ReadFrame(config, &s, MaxFrameBytes)
		if errors.Join(err, config.Close()) != nil {
			os.Exit(125)
		}
		RunExecStage(s)
		os.Exit(125)
	default:
		return false
	}
	return true
}

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
func runInit() {
	runtime.LockOSThread()
	control := os.NewFile(3, "helper-control")
	s := &initSession{control: control, files: make(map[*os.File]struct{}), phase: "configuration"}
	err := s.run()
	// 先保留原始阶段/errno，再关闭剩余 FD；此处不再启动任何用户代码。
	err = errors.Join(err, s.closeAll())
	if err != nil {
		var errno syscall.Errno
		errors.As(err, &errno)
		report := Event{Version: Version, Kind: "error", Phase: s.phase, Errno: uint32(errno)}
		if b, e := marshalEvent(report); e == nil {
			_, _ = control.Write(b)
		}
	}
	// Dispatch 随即 exit_group，控制通道写失败也不能返回普通服务循环。
}
func (s *initSession) run() error {
	src := os.NewFile(4, "stage-input")
	life := os.NewFile(5, "helper-liveness")
	s.own(src)
	// life 与 control 存活到 PID 1 退出，由内核关闭；主动关闭会触发断连监测。
	go exitOnDisconnect(life)
	var stage StageSpec
	if err := ReadFrame(src, &stage, MaxFrameBytes); err != nil {
		return err
	}
	if err := stage.Request.Validate(); err != nil {
		return err
	}
	if stage.PayloadUID <= 0 || stage.InitUID <= 0 || stage.PayloadUID == stage.InitUID || stage.PayloadGID <= 0 || stage.InitGID <= 0 || stage.PayloadGID == stage.InitGID || stage.WorkspaceBytes <= 0 || stage.WorkspaceBytes > 128<<20 || stage.WorkspaceInodes <= 0 || stage.WorkspaceInodes > 4096 {
		return fmt.Errorf("无效的受信启动配置")
	}
	s.phase = "rootfs-input"
	dir, stdin, err := prepareRoot(stage, src)
	// prepareRoot 失败时可能没有句柄；一旦交付即由 session 负责关闭。
	s.own(dir, stdin)
	if err != nil {
		return err
	}
	if err = s.close(src); err != nil {
		return err
	}
	if err = SendEvent(s.control, Event{Kind: "workspace"}, dir); err != nil {
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
func exitOnDisconnect(f *os.File) { var b [1]byte; _, _ = f.Read(b[:]); os.Exit(125) }

func (s *initSession) startPayload(stage StageSpec, stdin *os.File) error {
	s.phase = "resolve-command"
	path, err := resolveCommand(stage.Request.Command[0])
	if err != nil {
		return err
	}
	childSync, parentSync, err := SocketPair()
	if err != nil {
		return err
	}
	s.own(childSync, parentSync)
	s.parentSync = parentSync
	cfgR, cfgW, err := os.Pipe()
	if err != nil {
		return err
	}
	s.own(cfgR, cfgW)
	errR, errW, err := os.Pipe()
	if err != nil {
		return err
	}
	s.own(errR, errW)
	s.execErrors = errR
	spec := payloadSpec(stage, path)
	cmd := exec.Command("/.sandbox/launcher", "--isolated-exec")
	cmd.Env = []string{"GOMAXPROCS=1"}
	cmd.Dir = "/work"
	cmd.Stdin = stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.ExtraFiles = []*os.File{cfgR, childSync, errW}
	s.phase = "payload-start"
	if err = cmd.Start(); err != nil {
		return err
	}
	s.child = cmd
	if err = s.close(cfgR, childSync, errW, stdin); err != nil {
		return err
	}
	s.phase = "payload-config"
	if err = WriteFrame(cfgW, spec, MaxFrameBytes); err != nil {
		return err
	}
	return s.close(cfgW)
}

// 受信策略在启动器内生成；请求不能传入 UID、FD、过滤器或解除资源上限。
func payloadSpec(stage StageSpec, path string) ExecSpec {
	spec := ExecSpec{Path: path, Args: append([]string(nil), stage.Request.Command...),
		Env: append([]string{"PATH=/usr/bin:/bin", "HOME=/work", "TMPDIR=/tmp", "LANG=C"}, stage.Request.Env...),
		UID: stage.PayloadUID, GID: stage.PayloadGID, NoFile: 256, FileSizeBytes: 64 << 20,
		Profile: policy.Command, ErrorFD: 5, ReadyFD: 4}
	if path == "/usr/bin/g++" || path == "/usr/bin/gcc" {
		spec.Profile = policy.Toolchain
	}
	return spec
}
func (s *initSession) releasePayload(stage StageSpec) error {
	s.phase = "payload-ready"
	if err := readHandshake(s.parentSync, 'R'); err != nil {
		return err
	}
	s.phase = "supervisor-credentials"
	if errno := dropPrivileges(stage.InitUID, stage.InitGID); errno != 0 {
		return errno
	}
	s.phase = "supervisor-seccomp"
	if err := policy.Install(policy.Supervisor); err != nil {
		return err
	}
	// 过滤后不再传 FD，普通 write 保留 seqpacket 消息边界。
	if _, err := s.control.Write([]byte(`{"Version":1,"Kind":"ready"}`)); err != nil {
		return err
	}
	s.phase = "go-handshake"
	if err := readHandshake(s.control, 'G'); err != nil {
		return err
	}
	if _, err := s.parentSync.Write([]byte{'G'}); err != nil {
		return err
	}
	if err := s.close(s.parentSync); err != nil {
		return err
	}
	go exitOnDisconnect(s.control)
	return nil
}
func readHandshake(r io.Reader, want byte) error {
	var value [1]byte
	if _, err := io.ReadFull(r, value[:]); err != nil {
		return err
	}
	if value[0] != want {
		return fmt.Errorf("无效握手字节")
	}
	return nil
}
func (s *initSession) reportExit() error {
	s.phase = "payload-wait"
	waitErr := s.child.Wait()
	var exit *exec.ExitError
	if waitErr != nil && !errors.As(waitErr, &exit) {
		return waitErr
	}
	report := Event{Version: Version, Kind: "exit", ExitCode: s.child.ProcessState.ExitCode()}
	if ws, ok := s.child.ProcessState.Sys().(syscall.WaitStatus); ok && ws.Signaled() {
		report.Signal = int(ws.Signal())
	}
	var record [8]byte
	n, readErr := io.ReadFull(s.execErrors, record[:])
	report.ExecFailed = n != 0 || readErr != io.EOF
	if n == len(record) && readErr == nil {
		report.ExecStage = record[0]
		report.ExecErrno = binary.LittleEndian.Uint32(record[4:])
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
	for {
		var ws syscall.WaitStatus
		_, err := syscall.Wait4(-1, &ws, 0, nil)
		switch {
		case err == nil, errors.Is(err, syscall.EINTR):
			continue
		case errors.Is(err, syscall.ECHILD):
			select {} // 存活管道仍在监测 helper。
		default:
			return fmt.Errorf("回收 namespace 后代: %w", err)
		}
	}
}
