package helper

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
	"time"

	"cherry-oj/judge-engine/internal/sandbox/cgroup"
	"cherry-oj/judge-engine/internal/sandbox/launcher"
	"golang.org/x/sys/unix"
)

func execute(ctx context.Context, r launcher.Request, source io.Reader, c Config, makeGroup func(cgroup.Limits) (executionGroup, error), executable string, cancelInput context.CancelFunc) (executionResult, error) {
	x := newExecution(r, cancelInput)
	g, err := makeGroup(cgroup.Limits{MemoryBytes: r.Limits.MemoryBytes, MaxProcesses: r.Limits.MaxProcesses, CPUQuotaNs: cpuPeriod.Nanoseconds(), CPUPeriodNs: cpuPeriod.Nanoseconds()})
	if err != nil {
		x.fail(err)
		x.result.Error = err.Error()
		cancelInput()
		return x.result, err
	}
	x.group = g
	x.openOutput = func(dir *ownedFile, name string) (*os.File, int64, error) { return launcher.OpenOutput(dir.File, name) }
	x.shutdownControl = func() error {
		if x.control == nil {
			return nil
		}
		return unix.Shutdown(int(x.control.Fd()), unix.SHUT_RDWR)
	}
	x.prepare(r, source, c, executable, cancelInput)
	if x.runErr == nil {
		x.monitor(ctx, r)
	}
	return x.finish(ctx)
}

func (x *execution) prepare(r launcher.Request, source io.Reader, c Config, executable string, cancelInput context.CancelFunc) {
	rawCgroup, err := x.group.File()
	if err != nil {
		x.fail(err)
		return
	}
	x.cgfd = ownFile(rawCgroup)
	var nonce [16]byte
	if _, err = rand.Read(nonce[:]); err != nil {
		x.fail(err)
		return
	}
	x.mountpoint = filepath.Join(c.StateDir, "run-"+hex.EncodeToString(nonce[:]))
	if err = os.Mkdir(x.mountpoint, 0700); err != nil {
		x.mountpoint = ""
		x.fail(err)
		return
	}
	x.control, x.childControl, err = socketPair()
	if err != nil {
		x.fail(err)
		x.waited = true
		return
	}
	x.lifeR, x.lifeW, err = pipe()
	if err != nil {
		x.fail(err)
		x.waited = true
		return
	}
	x.dataR, x.dataW, err = pipe()
	if err != nil {
		x.fail(err)
		x.waited = true
		return
	}
	x.outR, x.outW, err = pipe()
	if err != nil {
		x.fail(err)
		x.waited = true
		return
	}
	x.errR, x.errW, err = pipe()
	if err != nil {
		x.fail(err)
		x.waited = true
		return
	}
	x.overflow = make(chan struct{}, 1)
	capture := func(f *ownedFile, limit int64) chan captureResult {
		done := make(chan captureResult, 1)
		go func() {
			w := &boundedCapture{limit: limit, overflow: x.overflow}
			_, e := io.Copy(w, f)
			done <- captureResult{bytes: w.data, err: e, exceeded: w.exceeded}
		}()
		return done
	}
	x.outDone = capture(x.outR, r.Limits.StdoutMaxBytes)
	x.errDone = capture(x.errR, r.Limits.StderrMaxBytes)
	cmd := exec.Command(executable, "--isolated-init")
	cmd.Env = []string{"GOMAXPROCS=1"}
	cmd.ExtraFiles = []*os.File{x.childControl.File, x.dataR.File, x.lifeR.File}
	cmd.Stdout = x.outW.File
	cmd.Stderr = x.errW.File
	cmd.SysProcAttr = &syscall.SysProcAttr{UseCgroupFD: true, CgroupFD: int(x.cgfd.Fd()), Cloneflags: unix.CLONE_NEWNS | unix.CLONE_NEWPID | unix.CLONE_NEWNET | unix.CLONE_NEWIPC | unix.CLONE_NEWUTS | unix.CLONE_NEWCGROUP}
	if err = cmd.Start(); err != nil {
		x.fail(err)
		x.waited = true
		return
	}
	x.waited = false
	x.waitDone = make(chan error, 1)
	go func() { x.waitDone <- cmd.Wait() }()
	if err := closeFiles(x.cgfd, x.childControl, x.lifeR, x.dataR, x.outW, x.errW); err != nil {
		x.fail(err)
		return
	}
	x.events = make(chan received, 4)
	go func() {
		defer close(x.events)
		for i := 0; i < 4; i++ {
			e, f, err := launcher.ReceiveEvent(x.control.File)
			x.events <- received{e, ownFile(f), err}
			if err != nil || e.Kind == "exit" {
				return
			}
		}
	}()
	x.feedDone = make(chan error, 1)
	x.feedFinished = make(chan struct{})
	stage := launcher.StageSpec{Request: r, RootFS: c.RootFS, MountPoint: x.mountpoint, Executable: executable, PayloadUID: c.PayloadUID, PayloadGID: c.PayloadGID, InitUID: c.InitUID, InitGID: c.InitGID, WorkspaceBytes: workspaceBytes, WorkspaceInodes: workspaceInodes}
	go func() {
		defer close(x.feedFinished)
		e := launcher.WriteFrame(x.dataW, stage, launcher.MaxFrameBytes)
		if e == nil {
			e = copyInput(x.dataW, source, r.InputBytes())
		}
		e = errors.Join(e, x.dataW.Close())
		x.feedDone <- e
		if e == nil {
			var extra [1]byte
			_, _ = source.Read(extra[:])
		}
		cancelInput()
	}()

}

func (x *execution) monitor(ctx context.Context, r launcher.Request) {
	ticker := time.NewTicker(cpuSampleInterval)
	defer ticker.Stop()
	wall := time.NewTimer(time.Until(x.started.Add(time.Duration(r.Limits.ClockNs))))
	defer wall.Stop()
	startup := time.NewTimer(time.Until(x.started.Add(startupTimeout)))
	defer startup.Stop()
	startupC := startup.C
	x.phase = awaitingWorkspace
	for {
		select {
		case <-ctx.Done():
			x.result.Reason = ReasonCancelled
			return
		case <-wall.C:
			x.result.Reason = ReasonWall
			return
		case <-startupC:
			x.fail(fmt.Errorf("隔离启动握手超时"))
			return
		case <-x.overflow:
			if x.phase < executing {
				x.fail(fmt.Errorf("可信启动器在放行前产生超限输出"))
				return
			}
			x.result.Reason = ReasonOutput
			return
		case e := <-x.feedDone:
			if e != nil {
				x.fail(fmt.Errorf("输入交付: %w", e))
				return
			}
			x.feedDone = nil
		case <-ticker.C:
			snap, e := x.group.Snapshot()
			if e != nil {
				x.fail(e)
				return
			}
			if snap.CPUNs >= r.Limits.CPUNs {
				x.result.Reason = ReasonCPU
				return
			}
		case waitErr := <-x.waitDone:
			x.waitDone = nil
			x.waited = isProcessExit(waitErr)
			if !x.waited {
				x.waitErr = waitErr
			}
			snap, e := x.group.Snapshot()
			if !isProcessExit(waitErr) {
				e = errors.Join(e, waitErr)
			}
			x.initLost = e == nil
			if e != nil || snap.OOMKill == 0 {
				x.fail(fmt.Errorf("可信 init 在报告退出事实前终止: %w", errors.Join(errInitLost, e)))
			}
			return
		case ev, ok := <-x.events:
			if !ok {
				x.fail(fmt.Errorf("启动控制通道提前关闭"))
				return
			}
			if x.handleEvent(ctx, r, ev) {
				return
			}
			if x.phase == executing {
				startup.Stop()
				startupC = nil
			}

		}
	}
}

func socketPair() (*ownedFile, *ownedFile, error) {
	a, b, err := launcher.SocketPair()
	return ownFile(a), ownFile(b), err
}

func (x *execution) handleEvent(ctx context.Context, r launcher.Request, ev received) bool {
	if ev.err != nil {
		x.initLost = errors.Is(ev.err, io.EOF)
		x.fail(ev.err)
		return true
	}
	if ev.event.Kind == "workspace" && x.phase == awaitingWorkspace && ev.dir != nil {
		x.workspace = ev.dir
		var fs unix.Statfs_t
		st, e := x.workspace.Stat()
		if e != nil || !st.IsDir() || unix.Fstatfs(int(x.workspace.Fd()), &fs) != nil || fs.Type != unix.TMPFS_MAGIC {
			x.fail(fmt.Errorf("启动器交付的工作目录无效"))
			return true
		}
		x.phase = awaitingReady
		return false
	}
	if ev.dir != nil {
		x.fail(errors.Join(fmt.Errorf("意外控制 FD"), ev.dir.Close()))
		return true
	}
	if ev.event.Kind == "error" {
		x.fail(fmt.Errorf("可信 init 阶段失败: %s errno=%d", ev.event.Phase, ev.event.Errno))
		return true
	}
	if ev.event.Kind == "ready" && x.phase == awaitingReady {
		if ctx.Err() != nil {
			x.result.Reason = ReasonCancelled
			return true
		}
		if time.Since(x.started) >= time.Duration(r.Limits.ClockNs) {
			x.result.Reason = ReasonWall
			return true
		}
		snap, e := x.group.Snapshot()
		if e != nil {
			x.fail(e)
			return true
		}
		if snap.CPUNs >= r.Limits.CPUNs {
			x.result.Reason = ReasonCPU
			return true
		}

		if _, e := x.control.Write([]byte{'G'}); e != nil {
			x.fail(e)
			return true
		}
		x.phase = executing
		return false
	}
	if ev.event.Kind == "exit" && x.phase == executing {
		x.result.ExitCode = ev.event.ExitCode
		x.result.Signal = ev.event.Signal
		if ev.event.ExecFailed {
			x.fail(fmt.Errorf("payload exec 启动失败: stage=%d errno=%d", ev.event.ExecStage, ev.event.ExecErrno))
		}
		return true
	}
	x.fail(fmt.Errorf("启动协议阶段错误"))
	return true
}
