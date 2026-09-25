package initproc

import (
	"fmt"
	"io"
	"os"
	"os/exec"

	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/isolator/privilege"
	"cherry-oj/judge-engine/isolator/seccomp"
	"cherry-oj/judge-engine/isolator/startup"
)

// rlimit 限制单进程 FD 和单文件大小，不替代 cgroup 整组计量。
const (
	payloadNoFile        = 256
	payloadFileSizeBytes = 64 << 20
)

func (s *initSession) startPayload(stage startup.StageSpec, stdin *os.File) error {
	s.phase = "resolve-command"
	path, err := resolveCommand(stage.Request.Command[0])
	if err != nil {
		return err
	}
	childSync, parentSync, err := startup.SocketPair()
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
	cmd := exec.Command("/.sandbox/launcher", startup.ExecArg)
	cmd.Env = []string{"GOMAXPROCS=1"}
	cmd.Dir = "/work"
	cmd.Stdin = stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	// ExtraFiles 是两个 re-exec 角色之间的私有通道；最终 exec 前均被标记 CLOEXEC。
	cmd.ExtraFiles = []*os.File{
		startup.ExecConfigFD - startup.ExtraFilesBaseFD: cfgR,
		startup.ExecReadyFD - startup.ExtraFilesBaseFD:  childSync,
		startup.ExecErrorFD - startup.ExtraFilesBaseFD:  errW,
	}
	s.phase = "payload-start"
	if err = cmd.Start(); err != nil {
		return err
	}
	s.child = cmd
	if err = s.close(cfgR, childSync, errW, stdin); err != nil {
		return err
	}
	s.phase = "payload-config"
	if err = hostexec.WriteFrame(cfgW, spec, hostexec.MaxFrameBytes); err != nil {
		return err
	}
	return s.close(cfgW)
}

// 受信策略在启动器内生成；请求不能传入 UID、FD、过滤器或解除资源上限。
func payloadSpec(stage startup.StageSpec, path string) startup.ExecSpec {
	spec := startup.ExecSpec{Path: path, Args: append([]string(nil), stage.Request.Command...),
		Env: append([]string{"PATH=/usr/bin:/bin", "HOME=/work", "TMPDIR=/tmp", "LANG=C"}, stage.Request.Env...),
		UID: stage.PayloadUID, GID: stage.PayloadGID, NoFile: payloadNoFile, FileSizeBytes: payloadFileSizeBytes,
		Profile: seccomp.Command, ErrorFD: startup.ExecErrorFD, ReadyFD: startup.ExecReadyFD}
	if path == "/usr/bin/g++" || path == "/usr/bin/gcc" {
		spec.Profile = seccomp.Toolchain
	}
	return spec
}

// releasePayload 必须先收到 exec 的 READY，再完成 init 自身降权并通知 helper。
// 用户命令只有在 helper 核对状态及剩余预算后才能拿到 GO；init 不能自行放行。
func (s *initSession) releasePayload(stage startup.StageSpec) error {
	s.phase = "payload-ready"
	if err := readHandshake(s.parentSync, startup.PayloadReady); err != nil {
		return err
	}
	s.phase = "supervisor-credentials"
	if errno := privilege.Drop(stage.InitUID, stage.InitGID); errno != 0 {
		return errno
	}
	s.phase = "supervisor-seccomp"
	if err := seccomp.Install(seccomp.Supervisor); err != nil {
		return err
	}
	// 过滤后不再传 FD，普通 write 保留 seqpacket 消息边界。
	if _, err := s.control.Write([]byte(startup.InitReadyMessage)); err != nil {
		return err
	}
	s.phase = "go-handshake"
	if err := readHandshake(s.control, startup.PayloadGo); err != nil {
		return err
	}
	if _, err := s.parentSync.Write([]byte{startup.PayloadGo}); err != nil {
		return err
	}
	if err := s.close(s.parentSync); err != nil {
		return err
	}
	go exitOnDisconnect(s.control)
	return nil
}
func readHandshake(r io.Reader, want byte) error {
	var value [startup.HandshakeBytes]byte
	if _, err := io.ReadFull(r, value[:]); err != nil {
		return err
	}
	if value[0] != want {
		return fmt.Errorf("invalid handshake byte")
	}
	return nil
}
