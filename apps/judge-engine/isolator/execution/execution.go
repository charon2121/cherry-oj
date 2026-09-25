//go:build linux && amd64

package execution

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"sync"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/isolator/cgroup"
)

const (
	cpuSampleInterval = 5 * time.Millisecond
	startupTimeout    = 3 * time.Second
	cleanupTimeout    = 5 * time.Second
)

var errInitLost = errors.New("init did not report the exit facts")

type Group interface {
	File() (*os.File, error)
	Snapshot() (cgroup.Snapshot, error)
	Stop(context.Context) (cgroup.Snapshot, error)
	Close(context.Context) error
}
type GroupFactory func(cgroup.Limits) (Group, error)

// executionProcess 隐藏 FD、握手与后台任务；Wait 只确认本进程及 I/O，
// 整组后代是否停止仍由 Group.Stop 确认。
type executionProcess interface {
	Start(Group) error
	Next(context.Context, supervisionTimers) processEvent
	Release() error
	CancelInput()
	Wait(context.Context) (processCompletion, error)
	TakeWorkspace() artifactSource
	Close() error
	RemoveMountpoint() error
}

// execution 独占一次命令的资源组与终止结论。Run 返回前完成回收；
// 成功移交的产物由 Result 持有，后续 Close 不再访问它们。
// lifecycle 锁拒绝并发 Run，并使异常兜底 Close 不与执行线程争抢资源。
type execution struct {
	lifecycle          sync.Mutex
	state              executionState
	plan               isolationPlan
	process            executionProcess
	makeGroup          GroupFactory
	group              Group
	started            time.Time
	result             Result
	runErr, cleanupErr error
	// supervision 是监督循环的初步结论；最终结论由 conclude 结合停组计量与等待结果给出。
	supervision supervisionOutcome
}

// Environment 是部署侧为一次执行固定下来的环境：可信 rootfs、宿主状态目录、
// 用于重新启动 P4 的本二进制路径，以及本槽位专属的 init/payload 身份。
type Environment struct {
	RootFS, StateDir, Executable             string
	PayloadUID, PayloadGID, InitUID, InitGID int
}

// Options 是一次执行的接线：Source 是请求的输入字节流，CancelInput 必须能解除对 Source 的阻塞读取，
// Groups 为本次执行创建资源组。
type Options struct {
	Environment
	Source      io.Reader
	CancelInput context.CancelFunc
	Groups      GroupFactory
}

// Run 执行一条已校验的请求，返回前完成全部回收。error 非 nil 表示回收无法确认，
// 调用方必须停止接单；命令本身的失败记录在 Result 里。
func Run(ctx context.Context, r hostexec.Request, options Options) (Result, error) {
	return newExecution(r, options).Run(ctx)
}

func newExecution(r hostexec.Request, options Options) *execution {
	// 计时覆盖计划构造、建组与启动，不能推迟到 ready 或用户 exec。
	started := time.Now()
	plan := newIsolationPlan(r, options.Environment)
	return &execution{plan: plan, process: newIsolatedProcess(plan, options.Source, options.CancelInput), makeGroup: options.Groups, started: started,
		result: Result{Result: hostexec.Result{Version: hostexec.Version, ExitCode: -1}}}
}

func (x *execution) Run(ctx context.Context) (Result, error) {
	if !x.lifecycle.TryLock() {
		return Result{}, fmt.Errorf("execution Run is already in progress")
	}
	defer x.lifecycle.Unlock()
	if x.state != executionNew {
		return Result{}, fmt.Errorf("execution can only Run once")
	}
	if err := x.transition(executionStarting); err != nil {
		return Result{}, err
	}
	// panic 时也尝试结束已取得的资源；正常路径显式检查 finish 的错误。
	defer func() {
		if x.state != executionFinished && x.state != executionCleanupFailed {
			x.supervision.fail(fmt.Errorf("the execution flow was interrupted unexpectedly"))
			_, x.cleanupErr = x.finish(context.Background())
		}
	}()
	g, err := x.makeGroup(x.plan.resources)
	if err != nil {
		// 连资源组都没建起来，没有可回收的执行环境，也就没有 finish 要走的顺序。
		x.fail(err)
		x.result.Error = err.Error()
		x.process.CancelInput()
		x.cleanupErr = err
		if e := x.transition(executionCleanupFailed); e != nil {
			x.cleanupErr = errors.Join(x.cleanupErr, e)
		}
		return x.takeResult(), err
	}
	x.group = g
	// 启动失败也是「这条命令怎么停下来的」的一种，和监督得出的结论走同一条路，
	// 否则它记下的平台故障会被最终结论覆盖掉。
	if err = x.process.Start(g); err != nil {
		x.supervision.fail(err)
	} else {
		x.supervision = x.supervise(ctx)
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
func (x *execution) takeResult() Result {
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
