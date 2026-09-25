package execution

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"syscall"

	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/isolator/startup"

	"golang.org/x/sys/unix"
)

// 正常顺序为 workspace/ready/exit；缓冲容纳整个有界接收循环，
// 监督提前结束时仍由 Wait 消费并关闭未接管的 FD。
const maxInitEvents = 4

const executionIDBytes = 16

// Start 取得的每个句柄立即登记；失败后仍必须先停组，再 Wait/Close。
func (p *isolatedProcess) Start(group Group) error {
	if p.started {
		return fmt.Errorf("isolatedProcess can only Start once")
	}
	p.started = true
	r, source := p.plan.request, p.source
	executable := p.plan.filesystem.executable
	if err := p.prepareInitResources(group); err != nil {
		return err
	}
	if err := p.startInit(r, executable); err != nil {
		return err
	}
	p.exchangeWithInit(r, source)
	return nil
}

func (p *isolatedProcess) prepareInitResources(group Group) error {
	rawCgroup, err := group.File()
	if err != nil {
		return err
	}
	p.cgroupFD = ownFile(rawCgroup)
	var nonce [executionIDBytes]byte
	if _, err = rand.Read(nonce[:]); err != nil {
		return err
	}
	p.mountpoint = filepath.Join(p.plan.filesystem.stateDir, "run-"+hex.EncodeToString(nonce[:]))
	if err = os.Mkdir(p.mountpoint, 0o700); err != nil {
		p.mountpoint = ""
		return err
	}
	p.control, p.childControl, err = socketPair()
	if err != nil {
		return err
	}
	p.lifeR, p.lifeW, err = pipe()
	if err != nil {
		return err
	}
	p.dataR, p.dataW, err = pipe()
	if err != nil {
		return err
	}
	p.outR, p.outW, err = pipe()
	if err != nil {
		return err
	}
	p.errR, p.errW, err = pipe()
	if err != nil {
		return err
	}
	return nil
}

func (p *isolatedProcess) startInit(r hostexec.Request, executable string) error {
	p.overflow = make(chan struct{}, 1)
	capture := func(f *ownedFile, limit int64) chan captureResult {
		done := make(chan captureResult, 1)
		go func() {
			w := &boundedCapture{limit: limit, overflow: p.overflow}
			_, e := io.Copy(w, f)
			done <- captureResult{bytes: w.data, err: e, exceeded: w.exceeded}
		}()
		return done
	}
	// 在启动子进程前接好输出收集，避免启动器写满管道后无法完成握手。
	p.stdoutDone = capture(p.outR, r.Limits.StdoutMaxBytes)
	p.stderrDone = capture(p.errR, r.Limits.StderrMaxBytes)
	cmd := exec.Command(executable, startup.InitArg)
	cmd.Env = []string{"GOMAXPROCS=1"}
	cmd.ExtraFiles = []*os.File{
		startup.InitControlFD - startup.ExtraFilesBaseFD:  p.childControl.File,
		startup.InitInputFD - startup.ExtraFilesBaseFD:    p.dataR.File,
		startup.InitLivenessFD - startup.ExtraFilesBaseFD: p.lifeR.File,
	}
	cmd.Stdout = p.outW.File
	cmd.Stderr = p.errW.File
	// 用内核的 UseCgroupFD 原子入组，不能等 Start 返回后再写 cgroup.procs：
	// 那会让新进程在限额和计量之外先运行。cgroup FD 不放入 ExtraFiles。
	cmd.SysProcAttr = &syscall.SysProcAttr{
		UseCgroupFD: true, CgroupFD: int(p.cgroupFD.Fd()),
		Cloneflags: p.plan.namespaces.cloneFlags,
	}
	if err := cmd.Start(); err != nil {
		return err
	}
	p.initStopped = false
	p.initExited = make(chan error, 1)
	go func() { p.initExited <- cmd.Wait() }()
	// 父进程释放只供子进程使用的管道端，否则子进程退出后读端仍等不到 EOF。
	if err := closeFiles(p.cgroupFD, p.childControl, p.lifeR, p.dataR, p.outW, p.errW); err != nil {
		return err
	}
	return nil
}

// 通道由启动方创建，Wait 等待接收与输入任务退出后才释放环境。
func (p *isolatedProcess) exchangeWithInit(r hostexec.Request, source io.Reader) {
	p.events = make(chan received, maxInitEvents)
	go func() {
		defer close(p.events)
		for i := 0; i < maxInitEvents; i++ {
			e, f, err := startup.ReceiveEvent(p.control.File)
			p.events <- received{e, ownFile(f), err}
			if err != nil || e.Kind == "exit" {
				return
			}
		}
	}()
	p.inputCopied = make(chan error, 1)
	p.inputFinished = make(chan struct{})
	stage := p.plan.stage(p.mountpoint)
	go func() {
		defer close(p.inputFinished)
		e := hostexec.WriteFrame(p.dataW, stage, hostexec.MaxFrameBytes)
		if e == nil {
			e = copyInput(p.dataW, source, r.InputBytes())
		}
		e = errors.Join(e, p.dataW.Close())
		p.inputCopied <- e
		if e == nil {
			// 输入复制完后继续感知客户端断连；正常收尾通过 cancelInput 推进读期限解除阻塞。
			// inputCopied 只代表复制结束，Wait 必须等 inputFinished 才能确认 goroutine 已退出。
			var extra [1]byte
			_, _ = source.Read(extra[:])
		}
		p.cancelInput()
	}()
}

func socketPair() (*ownedFile, *ownedFile, error) {
	a, b, err := startup.SocketPair()
	return ownFile(a), ownFile(b), err
}

func isolatedNamespaces() uintptr {
	return unix.CLONE_NEWNS | unix.CLONE_NEWPID | unix.CLONE_NEWNET | unix.CLONE_NEWIPC | unix.CLONE_NEWUTS | unix.CLONE_NEWCGROUP
}
func validateWorkspace(dir *ownedFile) error {
	var fs unix.Statfs_t
	st, err := dir.Stat()
	if err != nil || !st.IsDir() || unix.Fstatfs(int(dir.Fd()), &fs) != nil || fs.Type != unix.TMPFS_MAGIC {
		return fmt.Errorf("the launcher delivered an invalid working directory")
	}
	return nil
}
func (p *isolatedProcess) shutdownControl() error {
	if p.control == nil {
		return nil
	}
	return unix.Shutdown(int(p.control.Fd()), unix.SHUT_RDWR)
}
func (d workspaceDirectory) Open(name string) (*os.File, int64, error) {
	return OpenOutput(d.File.File, name)
}
