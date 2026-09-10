// Package runner 解析文件引用、编排单次执行并发布已确认的产物；不持有特权操作。
package runner

import (
	"context"
	"errors"
	"fmt"
	"io"
	"strings"
	"sync"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/container"
	"cherry-oj/judge-engine/internal/sandbox/store"
)

const MaxInlineBytes int64 = 1 << 20
const MaxInputBytes int64 = 64 << 20

func defaultLimits() contract.Limits {
	return contract.ExplicitLimits(contract.Limits{CPUNs: 1e9, ClockNs: 5e9, MemoryBytes: 128 << 20, MaxProcesses: 64, StdoutMaxBytes: 64 << 10, StderrMaxBytes: 64 << 10})
}

func Run(ctx context.Context, c container.Container, st store.Store, spec contract.RunSpec) (result contract.RunResult) {
	fail := func(status contract.Status, err error) contract.RunResult {
		if ctx.Err() != nil {
			status = contract.StatusInternalError
			err = errors.Join(err, ctx.Err())
		}
		return contract.RunResult{Status: status, Error: err.Error()}
	}
	limits, err := spec.Limits.WithDefaults(defaultLimits())
	if err != nil {
		return fail(contract.StatusInternalError, err)
	}
	if len(spec.Command) == 0 || len(spec.Inputs) > 128 || len(spec.Outputs)+len(spec.Artifacts) > 128 {
		return fail(contract.StatusWorkspaceError, fmt.Errorf("命令或文件数量无效"))
	}
	if err := ctx.Err(); err != nil {
		return fail(contract.StatusInternalError, err)
	}
	if limits.CPUNs == 0 || limits.ClockNs == 0 {
		return contract.RunResult{Status: contract.StatusTimeLimitExceeded}
	}
	if limits.MemoryBytes == 0 {
		return contract.RunResult{Status: contract.StatusMemoryLimitExceeded}
	}
	if limits.MaxProcesses == 0 {
		return fail(contract.StatusInternalError, fmt.Errorf("maxProcesses=0无法启动"))
	}
	// 所有后端都限制服务侧内存；Linux更严格的硬界由适配器校验，绝不悄悄截小预算。
	if limits.StdoutMaxBytes > 64<<20 || limits.StderrMaxBytes > 16<<20 {
		return fail(contract.StatusInternalError, fmt.Errorf("输出预算超过服务硬界"))
	}
	var remaining int64 = MaxInputBytes
	for name, src := range spec.Inputs {
		if err := putInput(ctx, c, st, name, src, &remaining); err != nil {
			return fail(contract.StatusWorkspaceError, err)
		}
	}
	var stdin io.ReadCloser
	if spec.Stdin != nil {
		stdin, err = resolve(st, *spec.Stdin)
		if err != nil {
			return fail(contract.StatusWorkspaceError, err)
		}
		// 输入源限于本地文件/内存；关闭可解除取消后的阻塞读取。
		closeInput := sync.OnceValue(stdin.Close)
		stop := context.AfterFunc(ctx, func() { closeInput() })
		defer func() {
			stop()
			if e := closeInput(); e != nil {
				for _, ref := range result.Artifacts {
					e = errors.Join(e, st.Delete(ref))
				}
				result.Outputs = nil
				result.Artifacts = nil
				result.Status = contract.StatusInternalError
				if result.Error != "" {
					e = errors.Join(errors.New(result.Error), e)
				}
				result.Error = e.Error()
			}
		}()
	}
	runCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	stdout, stderr := newCapWriter(limits.StdoutMaxBytes), newCapWriter(limits.StderrMaxBytes)
	stdout.onOverflow = cancel
	stderr.onOverflow = cancel
	outputs := make([]string, 0, len(spec.Outputs)+len(spec.Artifacts))
	seen := map[string]bool{}
	for _, list := range [][]string{spec.Outputs, spec.Artifacts} {
		for _, name := range list {
			if !seen[name] {
				outputs = append(outputs, name)
				seen[name] = true
			}
		}
	}
	var input io.Reader
	if stdin != nil {
		input = &budgetReader{r: stdin, remaining: remaining}
	}
	proc, err := c.Start(runCtx, container.Spec{Command: spec.Command, Env: spec.Env, Stdin: input, Stdout: stdout, Stderr: stderr, Limits: limits, Outputs: outputs})
	if err != nil {
		return fail(contract.StatusInternalError, err)
	}
	usage, waitErr := proc.Wait(runCtx)
	res := contract.RunResult{ExitCode: usage.ExitCode, Signal: usage.Signal, CPUNs: usage.CPUNs, ClockNs: usage.ClockNs, MemoryBytes: usage.MemoryBytes, Stdout: stdout.buf.String(), Stderr: stderr.buf.String()}
	res.Status = classify(limits, usage, stdout.overflow || stderr.overflow, ctx.Err())
	if waitErr != nil {
		res.Status = contract.StatusInternalError
		res.Error = waitErr.Error()
	}
	if res.Status != contract.StatusOK {
		return res
	}
	return collect(c, st, spec, res)
}

func putInput(ctx context.Context, c container.Container, st store.Store, name string, src contract.FileSource, remaining *int64) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	rc, err := resolve(st, src)
	if err != nil {
		return err
	}
	closeInput := sync.OnceValue(rc.Close)
	stop := context.AfterFunc(ctx, func() { closeInput() })
	defer stop()
	r := &budgetReader{r: rc, remaining: *remaining}
	err = c.PutFile(name, r, 0o755)
	*remaining = r.remaining
	return errors.Join(err, closeInput(), ctx.Err())
}

// 超限返回错误而不是伪装成EOF，防止接受被截断的源码/输入。
type budgetReader struct {
	r         io.Reader
	remaining int64
}

func (r *budgetReader) Read(p []byte) (int, error) {
	if len(p) == 0 {
		return 0, nil
	}
	if r.remaining == 0 {
		var b [1]byte
		n, e := r.r.Read(b[:])
		if n > 0 {
			return 0, fmt.Errorf("输入总量超限")
		}
		return 0, e
	}
	if int64(len(p)) > r.remaining {
		p = p[:r.remaining]
	}
	n, e := r.r.Read(p)
	r.remaining -= int64(n)
	return n, e
}

func collect(c container.Container, st store.Store, spec contract.RunSpec, res contract.RunResult) (result contract.RunResult) {
	result = res
	result.Outputs = map[string]string{}
	result.Artifacts = map[string]string{}
	// 只有全部产物成功才发布ref；中途失败删除已写入Store的文件。
	defer func() {
		if result.Status != contract.StatusOK {
			for _, ref := range result.Artifacts {
				if e := st.Delete(ref); e != nil {
					result.Error += "; 回滚产物: " + e.Error()
				}
			}
			result.Outputs = nil
			result.Artifacts = nil
		}
	}()
	remaining := MaxInlineBytes
	for _, name := range spec.Outputs {
		rc, e := c.GetFile(name)
		if e != nil {
			result.Status = contract.StatusWorkspaceError
			result.Error = e.Error()
			return
		}
		data, e := io.ReadAll(io.LimitReader(rc, remaining+1))
		e = errors.Join(e, rc.Close())
		if int64(len(data)) > remaining {
			e = errors.Join(e, fmt.Errorf("内联产物总量超过%d bytes", MaxInlineBytes))
		}
		if e != nil {
			result.Status = contract.StatusWorkspaceError
			result.Error = e.Error()
			return
		}
		remaining -= int64(len(data))
		result.Outputs[name] = string(data)
	}
	for _, name := range spec.Artifacts {
		if _, ok := result.Artifacts[name]; ok {
			continue
		}
		rc, e := c.GetFile(name)
		if e != nil {
			result.Status = contract.StatusWorkspaceError
			result.Error = e.Error()
			return
		}
		ref, e := st.Put(rc)
		// Put成功后即登记，随后Close失败也可以回滚。
		if e == nil {
			result.Artifacts[name] = ref
		}
		e = errors.Join(e, rc.Close())
		if e != nil {
			result.Status = contract.StatusInternalError
			result.Error = e.Error()
			return
		}
	}
	return
}

func resolve(st store.Store, src contract.FileSource) (io.ReadCloser, error) {
	switch {
	case src.Ref != "" && src.Text != "":
		return nil, fmt.Errorf("file source: ref & text只能二选一")
	case src.Ref != "":
		rc, e := st.Get(src.Ref)
		if e != nil {
			return nil, fmt.Errorf("get %q: %w", src.Ref, e)
		}
		return rc, nil
	default:
		return io.NopCloser(strings.NewReader(src.Text)), nil
	}
}

func classify(lim contract.Limits, u container.Usage, outOverflow bool, ctxErr error) contract.Status {
	switch {
	case ctxErr != nil || u.Reason == container.ReasonPlatform:
		return contract.StatusInternalError
	case u.OOMKilled:
		return contract.StatusMemoryLimitExceeded
	case u.Reason == container.ReasonCPU || u.Reason == container.ReasonWall:
		return contract.StatusTimeLimitExceeded
	case outOverflow || u.Reason == container.ReasonOutput:
		return contract.StatusOutputLimitExceeded
	case u.Reason != "":
		return contract.StatusInternalError
	case u.CPUNs > lim.CPUNs:
		return contract.StatusTimeLimitExceeded
	case !u.GroupAccounting && u.MemoryBytes > lim.MemoryBytes:
		return contract.StatusMemoryLimitExceeded
	case u.Signal != 0:
		return contract.StatusSignalled
	case u.ExitCode == 0:
		return contract.StatusOK
	default:
		return contract.StatusNonzeroExit
	}
}
