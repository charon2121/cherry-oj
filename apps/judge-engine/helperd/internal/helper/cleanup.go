package helper

import (
	"context"
	"errors"
	"fmt"
	"time"

	"cherry-oj/judge-engine/helperd/internal/cgroup"
	"cherry-oj/judge-engine/internal/hostexec"
)

// finish 必须显式调用：停止和等待是正常执行流程的一部分，不能藏在组装结果的 defer 内。
// 回收错误单独返回给服务使其停止接单；普通启动错误仅使本次执行失败。
//
// 本函数只负责**按内核要求的顺序收集事实并回收资源**：停组 → 等待 → 取产物 → 释放。
// 「这些事实意味着什么」交给 conclude——它不依赖顺序，可以单独穷举测试。
func (x *execution) finish(ctx context.Context) (executionResult, error) {
	if err := x.transition(executionFinishing); err != nil {
		x.fail(err)
	}
	// 请求可能已经取消，但回收必须继续；先解除输入阻塞，再使用独立期限收尾。
	x.process.CancelInput()
	cleanup, cancel := context.WithTimeout(context.Background(), cleanupTimeout)
	defer cancel()

	facts := executionFacts{supervision: x.supervision, cancelled: ctx.Err() != nil,
		budget: x.plan.request.Limits}
	var snap cgroup.Snapshot
	if x.group != nil {
		snap, facts.groupErr = x.group.Stop(cleanup)
	}
	facts.group = snap
	x.result.Usage = usageOf(snap)
	x.result.ClockNs = time.Since(x.started).Nanoseconds()

	completion, waitErr := x.process.Wait(cleanup)
	facts.completion = completion

	// 事实收齐，结论一次算出。
	verdict := conclude(facts)
	x.runErr = errors.Join(x.runErr, verdict.failure)
	x.result.Reason = verdict.reason
	x.result.Signal = verdict.signal
	if x.supervision.exited {
		x.result.ExitCode = x.supervision.exitCode
	}
	x.result.Cancelled = facts.cancelled || verdict.reason == hostexec.ReasonCancelled
	x.result.Stdout = completion.stdout
	x.result.Stderr = completion.stderr
	x.result.OutputExceeded = completion.outputExceeded

	cleanupErr := errors.Join(wrapError("停止资源组", facts.groupErr), waitErr)
	// 只有停组和全部等待成功，才从进程取得工作区并打开受控产物。
	var workspace artifactSource
	if cleanupErr == nil && completion.stopped && x.runErr == nil {
		workspace = x.process.TakeWorkspace()
		if workspace != nil {
			artifacts, err := collectArtifacts(workspace, x.plan.request.Outputs)
			x.result.artifacts = artifacts
			x.result.Outputs = artifacts.outputs
			x.fail(err)
		}
	}
	if workspace != nil {
		cleanupErr = errors.Join(cleanupErr, workspace.Close())
	}
	cleanupErr = errors.Join(cleanupErr, x.process.Close())
	if x.group != nil {
		cleanupErr = errors.Join(cleanupErr, wrapError("删除资源组", x.group.Close(cleanup)))
	}
	// 无法确认整组及 init 停止时保留宿主目录，不用递归删除掩盖残留。
	if facts.groupErr == nil && completion.stopped {
		cleanupErr = errors.Join(cleanupErr, x.process.RemoveMountpoint())
	}

	if cleanupErr != nil {
		x.fail(cleanupErr)
	}
	if x.runErr != nil {
		deliveryErr := x.result.Close()
		cleanupErr = errors.Join(cleanupErr, deliveryErr)
		x.fail(deliveryErr)
		x.result.Outputs = nil
		x.result.Error = x.runErr.Error()
	}
	x.cleanupErr = cleanupErr
	final := executionFinished
	if cleanupErr != nil {
		final = executionCleanupFailed
	}
	if err := x.transition(final); err != nil {
		x.cleanupErr = errors.Join(x.cleanupErr, err)
	}
	return x.takeResult(), x.cleanupErr
}

func wrapError(operation string, err error) error {
	if err == nil {
		return nil
	}
	return fmt.Errorf("%s: %w", operation, err)
}
