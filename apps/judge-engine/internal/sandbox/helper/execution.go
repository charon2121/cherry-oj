package helper

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"sync"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/internal/sandbox/cgroup"
)

const (
	cpuSampleInterval = 5 * time.Millisecond
	startupTimeout    = 3 * time.Second
	cleanupTimeout    = 5 * time.Second
)

var errInitLost = errors.New("init 未报告退出事实")

type executionGroup interface {
	File() (*os.File, error)
	Snapshot() (cgroup.Snapshot, error)
	Stop(context.Context) (cgroup.Snapshot, error)
	Close(context.Context) error
}
type groupFactory func(cgroup.Limits) (executionGroup, error)

// executionProcess 隐藏 FD、握手与后台任务；Wait 只确认本进程及 I/O，
// 整组后代是否停止仍由 executionGroup.Stop 确认。
type executionProcess interface {
	Start(executionGroup) error
	Next(context.Context, supervisionTimers) processEvent
	Release() error
	CancelInput()
	Wait(context.Context) (processCompletion, error)
	TakeWorkspace() artifactSource
	Close() error
	RemoveMountpoint() error
}

type executionState uint8

const (
	executionNew executionState = iota
	executionStarting
	executionRunning
	executionFinishing
	executionFinished
	executionCleanupFailed
)

// execution 独占一次命令的资源组与终止结论。Run 返回前完成回收；
// 成功移交的产物由 executionResult 持有，后续 Close 不再访问它们。
// lifecycle 锁拒绝并发 Run，并使异常兜底 Close 不与执行线程争抢资源。
type execution struct {
	lifecycle          sync.Mutex
	state              executionState
	plan               isolationPlan
	process            executionProcess
	makeGroup          groupFactory
	group              executionGroup
	started            time.Time
	result             executionResult
	runErr, cleanupErr error
	initReportLost     bool
}

type executionOptions struct {
	config      Config
	source      io.Reader
	executable  string
	cancelInput context.CancelFunc
	groups      groupFactory
}

func newExecution(r hostexec.Request, options executionOptions) *execution {
	// 计时覆盖计划构造、建组与启动，不能推迟到 ready 或用户 exec。
	started := time.Now()
	plan := newIsolationPlan(r, options.config, options.executable)
	return &execution{plan: plan, process: newIsolatedProcess(plan, options.source, options.cancelInput), makeGroup: options.groups, started: started,
		result: executionResult{Result: hostexec.Result{Version: hostexec.Version, ExitCode: -1}}}
}

func (x *execution) Run(ctx context.Context) (executionResult, error) {
	if !x.lifecycle.TryLock() {
		return executionResult{}, fmt.Errorf("execution Run 已在进行")
	}
	defer x.lifecycle.Unlock()
	if x.state != executionNew {
		return executionResult{}, fmt.Errorf("execution 只能 Run 一次")
	}
	x.state = executionStarting
	// panic 时也尝试结束已取得的资源；正常路径显式检查 finish 的错误。
	defer func() {
		if x.state != executionFinished && x.state != executionCleanupFailed {
			x.fail(fmt.Errorf("执行流程异常中断"))
			_, x.cleanupErr = x.finish(context.Background())
		}
	}()
	g, err := x.makeGroup(x.plan.resources)
	if err != nil {
		x.fail(err)
		x.result.Error = err.Error()
		x.process.CancelInput()
		x.cleanupErr = err
		x.state = executionCleanupFailed
		return x.takeResult(), err
	}
	x.group = g
	if err = x.process.Start(g); err != nil {
		x.fail(err)
	} else {
		x.supervise(ctx)
	}
	return x.finish(ctx)
}

func (x *execution) Close() error {
	x.lifecycle.Lock()
	defer x.lifecycle.Unlock()
	if x.state == executionNew {
		x.process.CancelInput()
		x.cleanupErr = x.process.Close()
		x.state = executionFinished
		if x.cleanupErr != nil {
			x.state = executionCleanupFailed
		}
	}
	return x.cleanupErr
}
func (x *execution) takeResult() executionResult {
	result := x.result
	x.result.artifacts = nil
	return result
}
func (x *execution) fail(err error) {
	if err != nil {
		x.runErr = errors.Join(x.runErr, err)
		x.result.Reason = hostexec.ReasonPlatform
	}
}
