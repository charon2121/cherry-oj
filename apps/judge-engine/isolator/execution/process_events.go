//go:build linux && amd64

package execution

import (
	"cherry-oj/judge-engine/isolator/startup"
	"context"
	"errors"
	"fmt"
	"io"
	"time"

	"golang.org/x/sys/unix"
)

type launchPhase uint8

const (
	awaitingWorkspace launchPhase = iota
	awaitingReady
	awaitingGo
	executing
)

type received struct {
	event startup.Event
	dir   *ownedFile
	err   error
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
			return processEvent{kind: processFailure, err: fmt.Errorf("the trusted launcher produced oversized output before handing over")}
		}
		return processEvent{kind: processOutputExceeded}
	case err := <-p.inputCopied:
		if err != nil {
			return processEvent{kind: processFailure, err: fmt.Errorf("deliver input: %w", err)}
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
			return processEvent{kind: processFailure, err: fmt.Errorf("the startup control channel closed early")}
		}
		return p.acceptEvent(ev)
	}
}

func (p *isolatedProcess) acceptEvent(ev received) processEvent {
	if ev.err != nil {
		return processEvent{kind: processFailure, err: ev.err, reportLost: errors.Is(ev.err, io.EOF)}
	}
	if ev.event.Kind == startup.EventWorkspace && p.phase == awaitingWorkspace && ev.dir != nil {
		p.workspace = workspaceDirectory{ev.dir}
		if err := validateWorkspace(ev.dir); err != nil {
			return processEvent{kind: processFailure, err: err}
		}
		p.phase = awaitingReady
		return processEvent{kind: processProgress}
	}
	if ev.dir != nil {
		return processEvent{kind: processFailure, err: errors.Join(fmt.Errorf("unexpected control FD"), ev.dir.Close())}
	}
	if ev.event.Kind == startup.EventError {
		return processEvent{kind: processFailure, err: fmt.Errorf("trusted init stage failed: %s errno=%d", ev.event.Phase, ev.event.Errno)}
	}
	if ev.event.Kind == startup.EventReady && p.phase == awaitingReady {
		p.phase = awaitingGo
		return processEvent{kind: processReady}
	}
	if ev.event.Kind == startup.EventExit && p.phase == executing {
		event := processEvent{kind: processExited, exitCode: ev.event.ExitCode, signal: ev.event.Signal}
		if ev.event.ExecFailed {
			event.err = fmt.Errorf("payload exec failed to start: stage=%d errno=%d", ev.event.ExecStage, ev.event.ExecErrno)
		}
		return event
	}
	return processEvent{kind: processFailure, err: fmt.Errorf("startup protocol stage error")}
}

// Release 只能在 ready 后由预算监督者调用，写成功才进入 executing。
func (p *isolatedProcess) Release() error {
	if p.phase != awaitingGo {
		return fmt.Errorf("startup protocol stage error")
	}
	if _, err := p.control.Write([]byte{startup.PayloadGo}); err != nil {
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

func validateWorkspace(dir *ownedFile) error {
	var fs unix.Statfs_t
	st, err := dir.Stat()
	if err != nil || !st.IsDir() || unix.Fstatfs(int(dir.Fd()), &fs) != nil || fs.Type != unix.TMPFS_MAGIC {
		return fmt.Errorf("the launcher delivered an invalid working directory")
	}
	return nil
}
