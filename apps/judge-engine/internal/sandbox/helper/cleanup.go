package helper

import (
	"context"
	"errors"
	"fmt"
	"syscall"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/internal/sandbox/cgroup"
)

// finish 必须显式调用：停止和等待是正常执行流程的一部分，不能藏在组装结果的 defer 内。
// 回收错误单独返回给服务使其停止接单；普通启动错误仅使本次执行失败。
func (x *execution) finish(ctx context.Context) (executionResult, error) {
	x.state = executionFinishing
	x.result.Cancelled = ctx.Err() != nil || x.result.Reason == hostexec.ReasonCancelled
	if x.result.Cancelled && x.result.Reason == "" {
		x.result.Reason = hostexec.ReasonCancelled
	}
	// 请求可能已经取消，但回收必须继续；先解除输入阻塞，再使用独立期限收尾。
	x.process.CancelInput()
	cleanup, cancel := context.WithTimeout(context.Background(), cleanupTimeout)
	defer cancel()

	var snap cgroup.Snapshot
	var stopErr error
	if x.group != nil {
		snap, stopErr = x.group.Stop(cleanup)
	}
	x.result.Usage = usageOf(snap)
	x.result.ClockNs = time.Since(x.started).Nanoseconds()
	// oom_kill 只说明本组有受害进程，也可能来自祖先或全局 OOM；
	// 缺少本任务 oom 证据时，不能把平台内存压力归为用户命令超限。
	if stopErr == nil && snap.OOMKill > 0 && snap.OOM == 0 {
		x.fail(fmt.Errorf("OOM victim without task-local OOM evidence"))
	}
	// 本任务 OOM 可能连 init 一起杀死；最终计量可解释丢失的退出报告。
	// 后面的 Wait/I/O/释放错误仍独立处理，不能被 OOM 掩盖。
	if stopErr == nil && x.initReportLost && snap.OOM > 0 && snap.OOMKill > 0 && x.result.Reason == hostexec.ReasonPlatform {
		x.runErr = nil
		x.result.Reason = ""
		x.result.Signal = int(syscall.SIGKILL)
	}
	if stopErr == nil && x.result.Reason == "" && snap.CPUNs >= x.plan.request.Limits.CPUNs {
		x.result.Reason = hostexec.ReasonCPU
	}

	completion, waitErr := x.process.Wait(cleanup)
	if x.initReportLost && completion.waitErr != nil && snap.OOMKill == 0 {
		x.fail(fmt.Errorf("init 提前退出: %w", completion.waitErr))
	}
	x.result.Stdout = completion.stdout
	x.result.Stderr = completion.stderr
	x.result.OutputExceeded = completion.outputExceeded
	if completion.outputExceeded && x.result.Reason == "" {
		x.result.Reason = hostexec.ReasonOutput
	}
	cleanupErr := errors.Join(wrapError("停止资源组", stopErr), waitErr)
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
	if stopErr == nil && completion.stopped {
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
	x.state = executionFinished
	if cleanupErr != nil {
		x.state = executionCleanupFailed
	}
	return x.takeResult(), cleanupErr
}

func wrapError(operation string, err error) error {
	if err == nil {
		return nil
	}
	return fmt.Errorf("%s: %w", operation, err)
}
