package helper

import (
	"context"
	"errors"
	"fmt"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
)

// supervise 观察这次执行怎么停下来，并返回初步结论。
// 它不写 execution 的结果字段：最终结论要等停组计量和等待结果一起判定，见 conclude。
func (x *execution) supervise(ctx context.Context) supervisionOutcome {
	var out supervisionOutcome
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
			out.reason = hostexec.ReasonCancelled
			return out
		case executionWallExpired:
			out.reason = hostexec.ReasonWall
			return out
		case executionStartupExpired:
			out.fail(fmt.Errorf("isolated startup handshake timed out"))
			return out
		case executionSampleDue:
			if reason, err := x.cpuBudget(); reason != "" {
				out.reason, out.failure = reason, err
				return out
			}
		case processOutputExceeded:
			out.reason = hostexec.ReasonOutput
			return out
		case processFailure:
			out.reportLost = event.reportLost
			out.fail(event.err)
			return out
		case processInitExited:
			snap, err := x.group.Snapshot()
			if !isProcessExit(event.err) {
				err = errors.Join(err, event.err)
			}
			// 快照成功且本组确有受害进程时，退出报告的丢失可能由 OOM 解释，留给 conclude 判定。
			out.reportLost = err == nil
			if err != nil || snap.OOMKill == 0 {
				out.fail(fmt.Errorf("trusted init died before reporting the exit facts: %w", errors.Join(errInitLost, err)))
			}
			return out
		case processReady:
			// ready 与超时可同时就绪，不能依据 select 的选择放行已超预算的命令。
			if ctx.Err() != nil {
				out.reason = hostexec.ReasonCancelled
				return out
			}
			if time.Since(x.started) >= time.Duration(x.plan.request.Limits.ClockNs) {
				out.reason = hostexec.ReasonWall
				return out
			}
			if reason, err := x.cpuBudget(); reason != "" {
				out.reason, out.failure = reason, err
				return out
			}
			if err := x.process.Release(); err != nil {
				out.fail(err)
				return out
			}
			if err := x.transition(executionRunning); err != nil {
				out.fail(err)
				return out
			}
			startup.Stop()
			timers.startup = nil
		case processExited:
			out.exited, out.exitCode, out.signal = true, event.exitCode, event.signal
			out.fail(event.err)
			return out
		}
	}
}

// cpuBudget 采样累计 CPU。取不到快照时按平台故障停止——拿不准预算就继续跑，
// 等于让一条已经超时的命令继续占着名额。
func (x *execution) cpuBudget() (hostexec.Reason, error) {
	snap, err := x.group.Snapshot()
	if err != nil {
		return hostexec.ReasonPlatform, err
	}
	if snap.CPUNs >= x.plan.request.Limits.CPUNs {
		return hostexec.ReasonCPU, nil
	}
	return "", nil
}
