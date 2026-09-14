package helper

import (
	"context"
	"errors"
	"fmt"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
)

func (x *execution) supervise(ctx context.Context) {
	ticker := time.NewTicker(cpuSampleInterval)
	defer ticker.Stop()
	wall := time.NewTimer(time.Until(x.started.Add(time.Duration(x.plan.request.Limits.ClockNs))))
	defer wall.Stop()
	startup := time.NewTimer(time.Until(x.started.Add(startupTimeout)))
	defer startup.Stop()
	timers := supervisionTimers{sample: ticker.C, wall: wall.C, startup: startup.C}
	for {
		event := x.process.Next(ctx, timers)
		switch event.kind {
		case executionCancelled:
			x.result.Reason = hostexec.ReasonCancelled
			return
		case executionWallExpired:
			x.result.Reason = hostexec.ReasonWall
			return
		case executionStartupExpired:
			x.fail(fmt.Errorf("隔离启动握手超时"))
			return
		case executionSampleDue:
			if x.cpuBudgetExceeded() {
				return
			}
		case processOutputExceeded:
			x.result.Reason = hostexec.ReasonOutput
			return
		case processFailure:
			x.initReportLost = event.reportLost
			x.fail(event.err)
			return
		case processInitExited:
			snap, err := x.group.Snapshot()
			if !isProcessExit(event.err) {
				err = errors.Join(err, event.err)
			}
			x.initReportLost = err == nil
			if err != nil || snap.OOMKill == 0 {
				x.fail(fmt.Errorf("可信 init 在报告退出事实前终止: %w", errors.Join(errInitLost, err)))
			}
			return
		case processReady:
			// ready 与超时可同时就绪，不能依据 select 的选择放行已超预算的命令。
			if ctx.Err() != nil {
				x.result.Reason = hostexec.ReasonCancelled
				return
			}
			if time.Since(x.started) >= time.Duration(x.plan.request.Limits.ClockNs) {
				x.result.Reason = hostexec.ReasonWall
				return
			}
			if x.cpuBudgetExceeded() {
				return
			}
			if err := x.process.Release(); err != nil {
				x.fail(err)
				return
			}
			x.state = executionRunning
			startup.Stop()
			timers.startup = nil
		case processExited:
			x.result.ExitCode = event.exitCode
			x.result.Signal = event.signal
			x.fail(event.err)
			return
		}
	}
}
func (x *execution) cpuBudgetExceeded() bool {
	snap, err := x.group.Snapshot()
	if err != nil {
		x.fail(err)
		return true
	}
	if snap.CPUNs >= x.plan.request.Limits.CPUNs {
		x.result.Reason = hostexec.ReasonCPU
		return true
	}
	return false
}
