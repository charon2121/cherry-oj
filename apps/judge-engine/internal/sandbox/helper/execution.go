package helper

import (
	"context"
	"errors"
	"fmt"
	"os"
	"syscall"
	"time"

	"cherry-oj/judge-engine/internal/sandbox/cgroup"
	"cherry-oj/judge-engine/internal/sandbox/launcher"
)

// 这些值是首版受控执行策略，不是调用者可覆盖的请求限额。
// 部署配置的可调范围在 TASK-099 冻结；在此集中，避免启动、监督和回收各自漂移。
const (
	cpuSampleInterval = 5 * time.Millisecond
	cpuPeriod         = 10 * time.Millisecond
	startupTimeout    = 3 * time.Second
	cleanupTimeout    = 5 * time.Second
	workspaceBytes    = 128 << 20
	workspaceInodes   = 4096
)

type launchPhase uint8

const (
	awaitingWorkspace launchPhase = iota
	awaitingReady
	executing
)

var errInitLost = errors.New("init 未报告退出事实")

type received struct {
	event launcher.Event
	dir   *ownedFile // 接收方取得所有权；消费后转给 workspace，否则由收尾关闭。
	err   error
}
type captureResult struct {
	bytes    []byte
	err      error
	exceeded bool
}

// executionGroup 是执行生命周期消费的接口；测试可以注入失败而不创建内核资源。
type executionGroup interface {
	File() (*os.File, error)
	Snapshot() (cgroup.Snapshot, error)
	Stop(context.Context) (cgroup.Snapshot, error)
	Close(context.Context) error
}

// execution 仅拥有一次执行的资源。所有状态由监督 goroutine 修改；后台线程只发送事件。
// 文件的 Close 可以与阻塞 I/O 并发，ownedFile 保证底层关闭一次并保留错误。
type execution struct {
	result      executionResult
	request     launcher.Request
	group       executionGroup
	cancelInput context.CancelFunc
	started     time.Time
	runErr      error

	waitErr error

	workspace             *ownedFile
	control, childControl *ownedFile
	dataR, dataW          *ownedFile
	outR, outW            *ownedFile
	errR, errW            *ownedFile
	lifeR, lifeW          *ownedFile
	cgfd                  *ownedFile

	outDone, errDone chan captureResult
	events           chan received
	feedDone         chan error
	feedFinished     chan struct{}
	waitDone         chan error
	overflow         chan struct{}
	mountpoint       string
	initLost, waited bool
	phase            launchPhase

	openOutput      func(*ownedFile, string) (*os.File, int64, error)
	shutdownControl func() error
}

func newExecution(r launcher.Request, cancel context.CancelFunc) *execution {
	return &execution{request: r, cancelInput: cancel, started: time.Now(), waited: true,
		result: executionResult{Result: Result{Version: launcher.Version, ExitCode: -1}}}
}

func (x *execution) fail(err error) {
	if err != nil {
		x.runErr = errors.Join(x.runErr, err)
		x.result.Reason = ReasonPlatform
	}
}

// finish 必须显式调用：停止和等待是正常执行流程的一部分，不能藏在组装结果的 defer 内。
// 回收错误单独返回给服务使其停止接单；普通启动错误仅使本次执行失败。
func (x *execution) finish(ctx context.Context) (executionResult, error) {
	x.result.Cancelled = ctx.Err() != nil || x.result.Reason == ReasonCancelled
	if x.result.Cancelled && x.result.Reason == "" {
		x.result.Reason = ReasonCancelled
	}
	x.cancelInput()
	cleanup, cancel := context.WithTimeout(context.Background(), cleanupTimeout)
	defer cancel()

	snap, stopErr := x.group.Stop(cleanup)
	x.result.Usage = snap
	x.result.ClockNs = time.Since(x.started).Nanoseconds()
	// oom_kill marks victims, including an ancestor/global OOM. It does not prove
	// this execution reached its own memory limit; preserve platform attribution.
	if stopErr == nil && snap.OOMKill > 0 && snap.OOM == 0 {
		x.fail(fmt.Errorf("OOM victim without task-local OOM evidence"))
	}
	if stopErr == nil && x.initLost && snap.OOM > 0 && snap.OOMKill > 0 && x.result.Reason == ReasonPlatform {
		x.runErr = nil
		x.result.Reason = ""
		x.result.Signal = int(syscall.SIGKILL)
	}
	if stopErr == nil && x.result.Reason == "" && snap.CPUNs >= x.request.Limits.CPUNs {
		x.result.Reason = ReasonCPU
	}
	cleanupErr := errors.Join(wrapError("停止资源组", stopErr), x.wait(cleanup))
	// 只有确认整组清空、直接子进程已回收且 I/O 已终止，才允许打开产物。
	if cleanupErr == nil && x.waited && x.workspace != nil && x.runErr == nil {
		x.fail(x.collectOutputs())
	}
	cleanupErr = errors.Join(cleanupErr, x.release(cleanup, stopErr == nil && x.waited))
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
	return x.result, cleanupErr
}

func wrapError(operation string, err error) error {
	if err == nil {
		return nil
	}
	return fmt.Errorf("%s: %w", operation, err)
}

// wait 先关闭写端、解除控制读取，再有界等待所有后台线程；不能先释放它们仍使用的目录。
func (x *execution) wait(ctx context.Context) error {
	result := wrapError("等待 init", x.waitErr)
	if x.shutdownControl != nil {
		result = errors.Join(result, wrapError("关闭控制通道", x.shutdownControl()))
	}
	result = errors.Join(result, closeFiles(x.childControl, x.dataR, x.dataW, x.outW, x.errW, x.lifeR, x.lifeW))
	if x.feedFinished != nil {
		select {
		case <-x.feedFinished:
		case <-ctx.Done():
			result = errors.Join(result, wrapError("等待输入结束", ctx.Err()))
		}
	}
	if x.waitDone != nil && !x.waited {
		select {
		case err := <-x.waitDone:
			if x.initLost && err != nil && x.result.Usage.OOMKill == 0 {
				x.fail(fmt.Errorf("init 提前退出: %w", err))
			}
			x.waited = isProcessExit(err)
			// Stop 主动终止 init，ExitError 是退出事实；其他错误意味着不能确认 wait 成功。
			if !isProcessExit(err) {
				result = errors.Join(result, wrapError("等待 init", err))
			}
		case <-ctx.Done():
			result = errors.Join(result, wrapError("等待 init", ctx.Err()))
		}
	}
	result = errors.Join(result, x.collectCapture(ctx, x.outDone, x.outR, true), x.collectCapture(ctx, x.errDone, x.errR, false))
	return errors.Join(result, x.drainEvents(ctx))
}

func (x *execution) collectCapture(ctx context.Context, ch <-chan captureResult, file *ownedFile, stdout bool) error {
	if ch == nil {
		return nil
	}
	var capture captureResult
	select {
	case capture = <-ch:
	case <-ctx.Done():
		return errors.Join(wrapError("等待输出结束", ctx.Err()), file.Close())
	}
	if stdout {
		x.result.Stdout = capture.bytes
	} else {
		x.result.Stderr = capture.bytes
	}
	x.result.OutputExceeded = x.result.OutputExceeded || capture.exceeded
	if capture.exceeded && x.result.Reason == "" {
		x.result.Reason = ReasonOutput
	}
	return wrapError("读取输出", capture.err)
}

func (x *execution) drainEvents(ctx context.Context) error {
	if x.events == nil {
		return nil
	}
	var result error
	for {
		select {
		case ev, ok := <-x.events:
			if !ok {
				return result
			}
			result = errors.Join(result, ev.dir.Close())
		case <-ctx.Done():
			return errors.Join(result, wrapError("等待控制接收结束", ctx.Err()))
		}
	}
}

// collectOutputs 只打开受控文件；所有权移交给 delivery，发布或 Store 引用不属于 helper。
func (x *execution) collectOutputs() error {
	var total int64
	for _, name := range x.request.Outputs {
		f, n, err := x.openOutput(x.workspace, name)
		// 当前传输协议允许命令失败时没有产物；Container.GetFile 缺文件语义由 TASK-097 对齐。
		if errors.Is(err, os.ErrNotExist) {
			continue
		}
		if err != nil {
			return fmt.Errorf("打开产物 %s: %w", name, err)
		}
		if n < 0 || n > launcher.MaxArtifactBytes-total {
			return errors.Join(fmt.Errorf("产物总量超限: %s", name), f.Close())
		}
		total += n
		x.result.Outputs = append(x.result.Outputs, Output{Path: name, SizeBytes: n})
		x.result.files = append(x.result.files, ownFile(f))
	}
	return nil
}

func (x *execution) release(ctx context.Context, removeMountpoint bool) error {
	result := closeFiles(x.cgfd, x.workspace, x.control, x.outR, x.errR)
	result = errors.Join(result, wrapError("删除资源组", x.group.Close(ctx)))
	if x.mountpoint != "" && removeMountpoint {
		result = errors.Join(result, wrapError("删除空挂载目录", os.Remove(x.mountpoint)))
	}
	return result
}
