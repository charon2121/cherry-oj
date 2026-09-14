package helper

import (
	"context"
	"errors"
	"fmt"
	"io"
	"time"

	"cherry-oj/judge-engine/internal/sandbox/launcher"
)

type launchPhase uint8

const (
	awaitingWorkspace launchPhase = iota
	awaitingReady
	awaitingGo
	executing
)

type received struct {
	event launcher.Event
	dir   *ownedFile
	err   error
}
type captureResult struct {
	bytes    []byte
	err      error
	exceeded bool
}

// 定时器由 execution 持有。Next 同时等待进程事实与预算唤醒，
// 避免多加一个转发 goroutine 改变事件时序和回收条件。
type supervisionTimers struct{ sample, wall, startup <-chan time.Time }
type processEventKind uint8

const (
	processProgress processEventKind = iota
	processReady
	processExited
	processInitExited
	processFailure
	processOutputExceeded
	executionCancelled
	executionWallExpired
	executionStartupExpired
	executionSampleDue
)

type processEvent struct {
	kind             processEventKind
	exitCode, signal int
	err              error
	reportLost       bool
}
type processCompletion struct {
	stopped        bool
	waitErr        error
	stdout, stderr []byte
	outputExceeded bool
}

// isolatedProcess 是 P3 的进程句柄，不是 P4 内的 initSession。
// 主 goroutine 独占协议状态，后台任务只发送事实，所有 FD 均在此取得和释放。
type isolatedProcess struct {
	plan        isolationPlan
	source      io.Reader
	cancelInput context.CancelFunc
	started     bool
	completion  processCompletion
	waitDone    bool
	waitResult  error
	closeDone   bool
	closeErr    error
	waitErr     error

	workspace             artifactSource
	control, childControl *ownedFile
	dataR, dataW          *ownedFile
	outR, outW            *ownedFile
	errR, errW            *ownedFile
	lifeR, lifeW          *ownedFile
	cgroupFD              *ownedFile

	stdoutDone, stderrDone chan captureResult
	events                 chan received
	inputCopied            chan error
	inputFinished          chan struct{}
	initExited             chan error
	overflow               chan struct{}
	mountpoint             string
	initStopped            bool // 尚未启动 init 或已确认它退出时为 true。
	phase                  launchPhase
}

func newIsolatedProcess(plan isolationPlan, source io.Reader, cancel context.CancelFunc) *isolatedProcess {
	return &isolatedProcess{plan: plan, source: source, cancelInput: cancel, initStopped: true}
}
func (p *isolatedProcess) CancelInput() { p.cancelInput() }
func (p *isolatedProcess) Next(ctx context.Context, timers supervisionTimers) processEvent {
	select {
	case <-ctx.Done():
		return processEvent{kind: executionCancelled}
	case <-timers.wall:
		return processEvent{kind: executionWallExpired}
	case <-timers.startup:
		return processEvent{kind: executionStartupExpired}
	case <-timers.sample:
		return processEvent{kind: executionSampleDue}
	case <-p.overflow:
		if p.phase < executing {
			return processEvent{kind: processFailure, err: fmt.Errorf("可信启动器在放行前产生超限输出")}
		}
		return processEvent{kind: processOutputExceeded}
	case err := <-p.inputCopied:
		if err != nil {
			return processEvent{kind: processFailure, err: fmt.Errorf("输入交付: %w", err)}
		}
		p.inputCopied = nil
		return processEvent{kind: processProgress}
	case err := <-p.initExited:
		p.initExited = nil
		p.initStopped = isProcessExit(err)
		if !p.initStopped {
			p.waitErr = err
		}
		return processEvent{kind: processInitExited, err: err}
	case ev, ok := <-p.events:
		if !ok {
			return processEvent{kind: processFailure, err: fmt.Errorf("启动控制通道提前关闭")}
		}
		return p.acceptEvent(ev)
	}
}

func (p *isolatedProcess) acceptEvent(ev received) processEvent {
	if ev.err != nil {
		return processEvent{kind: processFailure, err: ev.err, reportLost: errors.Is(ev.err, io.EOF)}
	}
	if ev.event.Kind == "workspace" && p.phase == awaitingWorkspace && ev.dir != nil {
		p.workspace = workspaceDirectory{ev.dir}
		if err := validateWorkspace(ev.dir); err != nil {
			return processEvent{kind: processFailure, err: err}
		}
		p.phase = awaitingReady
		return processEvent{kind: processProgress}
	}
	if ev.dir != nil {
		return processEvent{kind: processFailure, err: errors.Join(fmt.Errorf("意外控制 FD"), ev.dir.Close())}
	}
	if ev.event.Kind == "error" {
		return processEvent{kind: processFailure, err: fmt.Errorf("可信 init 阶段失败: %s errno=%d", ev.event.Phase, ev.event.Errno)}
	}
	if ev.event.Kind == "ready" && p.phase == awaitingReady {
		p.phase = awaitingGo
		return processEvent{kind: processReady}
	}
	if ev.event.Kind == "exit" && p.phase == executing {
		event := processEvent{kind: processExited, exitCode: ev.event.ExitCode, signal: ev.event.Signal}
		if ev.event.ExecFailed {
			event.err = fmt.Errorf("payload exec 启动失败: stage=%d errno=%d", ev.event.ExecStage, ev.event.ExecErrno)
		}
		return event
	}
	return processEvent{kind: processFailure, err: fmt.Errorf("启动协议阶段错误")}
}

// Release 只能在 ready 后由预算监督者调用，写成功才进入 executing。
func (p *isolatedProcess) Release() error {
	if p.phase != awaitingGo {
		return fmt.Errorf("启动协议阶段错误")
	}
	if _, err := p.control.Write([]byte{launcher.PayloadGo}); err != nil {
		return err
	}
	p.phase = executing
	return nil
}

// TakeWorkspace 只在 Wait 确认停止及 I/O 完成后移交；第二次不再交付。
func (p *isolatedProcess) TakeWorkspace() artifactSource {
	if !p.waitDone || p.waitResult != nil || !p.completion.stopped {
		return nil
	}
	dir := p.workspace
	p.workspace = nil
	return dir
}
