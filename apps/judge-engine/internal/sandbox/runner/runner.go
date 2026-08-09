package runner

import (
	"bytes"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/container"
	"cherry-oj/judge-engine/internal/sandbox/store"
	"context"
	"fmt"
	"io"
	"strings"
	"time"
)

// 没设上限时的兜底，避免 Limits 零值被当成「一个字节都不许写」
const defaultOutputMaxBytes = 64 << 10 // 64 KiB

// 收集子进程的 stdout, 超过限制之后进行截断
type capWriter struct {
	buf      bytes.Buffer
	max      int64 // 最大能写多少字节
	current  int64 // 当前写了多少字节
	overflow bool
}

func newCapWriter(max int64) *capWriter {
	if max <= 0 {
		max = defaultOutputMaxBytes
	}
	return &capWriter{max: max}
}

func (w *capWriter) Write(p []byte) (int, error) {

	remain := w.max - w.current

	// 只有真的有字节要被丢掉才算溢出。
	// 写「满」不等于写「溢出」——恰好写到上限一个字节没丢，不该报 OLE。
	if int64(len(p)) > remain {
		w.overflow = true
	}

	if remain > 0 {
		take := min(int64(len(p)), remain)
		w.buf.Write(p[:take])
		w.current += take
	}

	return len(p), nil
}

// Run 函数是 sandbox 执行一条命令的完整生命周期
// 其中，Container 是具体的执行者，Store 和 RunSpec 互相配合，给 Container 的执行提供配置 / 依赖的文件
//
// 整个过程分四个大阶段
//
// 阶段一（准备）：
// 1. 将 spec.Inputs 中的所有 FileSource 解析出 Reader，写入 Container 内
// 2. 准备命令执行过程中的标准输入
// 3. 创建收集器，收集命令执行的标准输出和标准错误
// 4. 创建闹钟，命令超时未结束时移除杀进程
// 阶段二（执行）：
// 1. 运行 RunSpec 中指定的命令
// 阶段三（判定）：
// 1. 根据执行结果，组装 RunResult
// 阶段四（收尾）：
// 收命令运行成功之后的产物
func Run(ctx context.Context, c container.Container, st store.Store, spec contract.RunSpec) contract.RunResult {

	fail := func(s contract.Status, err error) contract.RunResult {
		return contract.RunResult{
			Status: s,
			Error:  err.Error(),
		}
	}

	// 铺 inputs: 把每个文件写进工作目录
	// 1. ref 从 store 读
	// 2. text 直接用
	for name, src := range spec.Inputs {
		rc, err := resolve(st, src)
		if err != nil {
			return fail(contract.StatusWorkspaceError, err)
		}
		err = c.PutFile(name, rc, 0o755)
		rc.Close()
		if err != nil {
			return fail(contract.StatusWorkspaceError, err)
		}
	}

	// 准备标准输入
	var stdin io.Reader
	if spec.Stdin != nil {
		rc, err := resolve(st, *spec.Stdin)
		if err != nil {
			return fail(contract.StatusWorkspaceError, err)
		}
		defer rc.Close()
		stdin = rc
	}

	// 创建带上限的收集器，收集标准错误和标准输出
	stdout := newCapWriter(spec.Limits.StdoutMaxBytes)
	stderr := newCapWriter(spec.Limits.StderrMaxBytes)

	// 墙钟超时时间，程序最多能执行的时间。
	// ClockNs 没设（=0）就是「不限时」——不能直接 WithTimeout(ctx, 0)，那会立刻超时。
	var runCtx context.Context
	var cancel context.CancelFunc
	if spec.Limits.ClockNs > 0 {
		runCtx, cancel = context.WithTimeout(ctx, time.Duration(spec.Limits.ClockNs))
	} else {
		runCtx, cancel = context.WithCancel(ctx)
	}
	defer cancel()

	// 起进程 + 等结束 + 量墙钟时间
	start := time.Now()

	proc, err := c.Start(runCtx, container.Spec{
		Command: spec.Command,
		Env:     spec.Env,
		Stdin:   stdin,
		Stdout:  stdout,
		Stderr:  stderr,
		Limits:  spec.Limits,
	})

	if err != nil {
		return fail(contract.StatusInternalError, err)
	}

	usage, _ := proc.Wait(runCtx)
	wall := time.Since(start).Nanoseconds()

	status := classify(spec.Limits, usage, stdout.overflow || stderr.overflow, runCtx.Err())

	res := contract.RunResult{
		Status:      status,
		ExitCode:    usage.ExitCode,
		Signal:      usage.Signal,
		CPUNs:       usage.CPUNs,
		ClockNs:     wall,
		MemoryBytes: usage.MemoryBytes,
		Stdout:      stdout.buf.String(),
		Stderr:      stderr.buf.String(),
	}

	if res.Status != contract.StatusOK {
		return res
	}

	// 收产物（1）：内联取回
	res.Outputs = make(map[string]string)
	for _, name := range spec.Outputs {
		rc, err := c.GetFile(name)
		if err != nil {
			res.Status = contract.StatusWorkspaceError
			res.Error = err.Error()
			return res
		}
		data, err := io.ReadAll(rc)
		rc.Close()
		if err != nil {
			res.Status = contract.StatusWorkspaceError
			res.Error = err.Error()
			return res // 读了半截的内容不能算数
		}
		res.Outputs[name] = string(data)
	}

	// 收产物（2）：存储到 store 中
	res.Artifacts = make(map[string]string)

	for _, name := range spec.Artifacts {
		rc, err := c.GetFile(name)
		if err != nil {
			res.Status = contract.StatusWorkspaceError
			res.Error = err.Error()
			return res
		}
		ref, err := st.Put(rc)
		rc.Close()
		if err != nil {
			res.Status = contract.StatusInternalError // Put 挂 = 基础设施，别写成 WorkspaceError
			res.Error = err.Error()
			return res
		}
		res.Artifacts[name] = ref
	}

	return res
}

func resolve(st store.Store, src contract.FileSource) (io.ReadCloser, error) {
	switch {
	case src.Ref != "" && src.Text != "":
		return nil, fmt.Errorf("file source: ref & text 只能二选一")
	case src.Ref != "":
		rc, err := st.Get(src.Ref)
		if err != nil {
			return nil, fmt.Errorf("get %q: %w", src.Ref, err)
		}
		return rc, nil
	default:
		return io.NopCloser(strings.NewReader(src.Text)), nil
	}
}

func classify(lim contract.Limits, u container.Usage, outOverflow bool, ctxErr error) contract.Status {
	switch {
	case ctxErr == context.DeadlineExceeded:
		return contract.StatusTimeLimitExceeded // 墙钟超时
	case lim.CPUNs > 0 && u.CPUNs > lim.CPUNs:
		return contract.StatusTimeLimitExceeded // CPU 超时
	case lim.MemoryBytes > 0 && u.MemoryBytes > lim.MemoryBytes:
		return contract.StatusMemoryLimitExceeded // 需要靠 cgroup 才准
	case outOverflow:
		return contract.StatusOutputLimitExceeded // 输出超限
	case u.Signal != 0:
		return contract.StatusSignalled // SIGSEGV 等
	case u.ExitCode == 0:
		return contract.StatusOK
	default:
		return contract.StatusNonzeroExit // 非 0 退出
	}
}
