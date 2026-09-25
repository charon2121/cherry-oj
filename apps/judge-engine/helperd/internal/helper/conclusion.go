//go:build linux && amd64

package helper

import (
	"errors"
	"fmt"
	"syscall"

	"cherry-oj/judge-engine/helperd/internal/cgroup"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
)

var errAncestorOOM = errors.New("OOM victim without task-local OOM evidence")

// supervisionOutcome 是监督循环得出的初步结论：这条命令是怎么停下来的。
// 它只记录监督期间观察到的事实，最终结论要等停组计量和等待结果一起判定。
type supervisionOutcome struct {
	reason   hostexec.Reason
	failure  error
	exited   bool // payload 报告过退出事实
	exitCode int
	signal   int
	// reportLost 表示可信 init 没能报告退出事实。它可能由本任务 OOM 造成，
	// 因此不能立刻判成平台故障——要等最终计量给出解释。
	reportLost bool
}

func (o *supervisionOutcome) fail(err error) {
	if err != nil {
		o.failure = errors.Join(o.failure, err)
		o.reason = hostexec.ReasonPlatform
	}
}

// executionFacts 是一次执行结束后收集到的全部客观事实，构造完即不再变化。
// 把它和结论分开，是因为收集事实必须按内核要求的顺序命令式地做（停组 → 等待 → 取产物），
// 而「这些事实意味着什么」不依赖顺序，可以单独判定，也可以单独测试。
type executionFacts struct {
	supervision supervisionOutcome
	cancelled   bool            // 请求上下文已取消
	group       cgroup.Snapshot // 停组确认后的最终计量
	groupErr    error           // 停组失败：计量不可信
	budget      contract.Limits
	completion  processCompletion
}

// conclusion 是这次执行的终止结论。failure 非空表示平台故障，此时不得交付产物。
type conclusion struct {
	reason  hostexec.Reason
	signal  int
	failure error
}

func (c *conclusion) fail(err error) {
	c.failure = errors.Join(c.failure, err)
	c.reason = hostexec.ReasonPlatform
}

// conclude 从事实推出结论。它不做输入输出、不改写任何状态、不依赖调用顺序，
// 因此可以在任意平台上以表驱动方式穷举——见 conclusion_test.go 的对照表。
func conclude(f executionFacts) conclusion {
	c := conclusion{reason: f.supervision.reason, signal: f.supervision.signal, failure: f.supervision.failure}
	// 取消是调用方主动放弃，不是这次执行超了预算；已有更具体的原因时不覆盖。
	if f.cancelled && c.reason == "" {
		c.reason = hostexec.ReasonCancelled
	}
	if f.groupErr == nil {
		// oom_kill 只说明本组有受害进程，也可能来自祖先或全局 OOM；
		// 缺少本任务 oom 证据时，不能把平台内存压力归为用户命令超限。
		if f.group.OOMKill > 0 && f.group.OOM == 0 {
			c.fail(errAncestorOOM)
		}
		// 本任务 OOM 可能连 init 一起杀死，最终计量正好解释了丢失的退出报告，
		// 于是撤销上面按平台故障记下的结论。后面的等待与回收失败仍单独处理，不被掩盖。
		if f.supervision.reportLost && f.group.OOM > 0 && f.group.OOMKill > 0 && c.reason == hostexec.ReasonPlatform {
			c.failure, c.reason = nil, ""
			c.signal = int(syscall.SIGKILL)
		}
		// 采样可能恰好错过最后一段 CPU，用最终计量兜底。
		if c.reason == "" && f.group.CPUNs >= f.budget.CPUNs {
			c.reason = hostexec.ReasonCPU
		}
	}
	// 既没有 OOM 证据又丢了退出报告，init 提前退出就只能算平台故障。
	if f.supervision.reportLost && f.completion.waitErr != nil && f.group.OOMKill == 0 {
		c.fail(fmt.Errorf("init exited early: %w", f.completion.waitErr))
	}
	if f.completion.outputExceeded && c.reason == "" {
		c.reason = hostexec.ReasonOutput
	}
	return c
}
