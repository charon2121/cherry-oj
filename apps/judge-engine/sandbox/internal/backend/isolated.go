package backend

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"sync"

	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/internal/hostexec/client"
	"cherry-oj/judge-engine/sandbox/internal/workspace"
)

// Isolated 通过本机执行协议把命令交给特权 helper。本进程不持有任何特权。
//
// 它可以并发使用：每次 Execute 在暂存根下分配自己的目录，彼此不共享状态。
type Isolated struct {
	socket string
	root   string
}

// NewIsolated 创建隔离后端的客户端。单次执行目录从 ws 分配——暂存根的独占锁、
// 所有权与权限核验都属于 Workspace，本后端不再重复一遍那套检查。
// 本构造不会自动退回零隔离后端。
func NewIsolated(socket string, ws *workspace.Workspace) (*Isolated, error) {
	if socket == "" || ws == nil {
		return nil, fmt.Errorf("helper socket and staging root must not be empty")
	}
	return &Isolated{socket: socket, root: ws.Root()}, nil
}

// execution 是一次执行独占的暂存目录与文件句柄，随 Execute 创建和销毁。
// 这里暂存的是服务自己创建的普通文件，用户代码从不接触此目录；
// 逻辑路径只作 map key，随机物理文件名不会拼入用户路径。
type execution struct {
	dir     string
	files   []*os.File
	bytes   int64
	outputs map[string]string
}

func (x *execution) close() error {
	var err error
	for _, f := range x.files {
		err = errors.Join(err, f.Close())
	}
	return errors.Join(err, os.RemoveAll(x.dir))
}

// spool 先取得长度，使 helper 可按声明大小划分连续输入流；limit+1 用于发现超限，
// 不能在上限处伪装 EOF，否则被截断的源码会被当作完整输入交付。
func (x *execution) spool(r io.Reader, limit int64) (*os.File, int64, error) {
	if r == nil {
		return nil, 0, fmt.Errorf("the input stream is nil")
	}
	f, err := os.CreateTemp(x.dir, "data-")
	if err != nil {
		return nil, 0, err
	}
	n, err := io.Copy(f, io.LimitReader(r, limit+1))
	if err == nil && n > limit {
		err = fmt.Errorf("total file size exceeds %d bytes", limit)
	}
	if err == nil {
		_, err = f.Seek(0, io.SeekStart)
	}
	if err != nil {
		return nil, 0, errors.Join(err, f.Close(), os.Remove(f.Name()))
	}
	return f, n, nil
}

func (b *Isolated) Execute(ctx context.Context, j Job, sink OutputSink) (Facts, error) {
	dir, err := os.MkdirTemp(b.root, "execution-")
	if err != nil {
		return Facts{}, err
	}
	x := &execution{dir: dir, outputs: map[string]string{}}
	// 目录回收失败意味着残留文件会与后续执行重叠，属于回收未确认。
	defer func() {
		if e := x.close(); e != nil {
			err = errors.Join(err, cleanupFailed("%w", e))
		}
	}()

	request, input, err := b.prepare(x, j)
	if err != nil {
		return Facts{}, err
	}
	result, callErr := client.Call(ctx, b.socket, request, input,
		func(o hostexec.Output, r io.Reader) error { return x.receive(o, r) })

	facts := factsOf(result)
	callErr = errors.Join(callErr, checkResult(result, j))
	if callErr != nil {
		return facts, callErr
	}
	if err := writeStreams(j, result); err != nil {
		return facts, err
	}
	return facts, x.deliver(facts, j.Outputs, sink)
}

// prepare 把输入逐个落到暂存目录，并按与 Inputs 相同的顺序拼成连续输入流；
// stdin 最后追加，线上没有逐文件分隔符，因此声明长度必须与实际字节一致。
func (b *Isolated) prepare(x *execution, j Job) (hostexec.Request, io.ReadCloser, error) {
	request := hostexec.Request{Version: hostexec.Version, Command: j.Command, Env: j.Env,
		Outputs: j.Outputs, Limits: j.Limits}
	seen := map[string]bool{}
	for _, in := range j.Inputs {
		if !hostexec.ValidPath(in.Name) || seen[in.Name] {
			return request, nil, fmt.Errorf("invalid or duplicate input path: %q", in.Name)
		}
		seen[in.Name] = true
		f, n, err := x.spool(in.Reader, hostexec.MaxInputBytes-x.bytes)
		if err != nil {
			return request, nil, err
		}
		x.files = append(x.files, f)
		x.bytes += n
		request.Inputs = append(request.Inputs, hostexec.Input{Path: in.Name, SizeBytes: n, Executable: in.Executable})
	}
	if j.Stdin != nil {
		f, n, err := x.spool(j.Stdin.Reader, hostexec.MaxInputBytes-x.bytes)
		if err != nil {
			return request, nil, err
		}
		x.files = append(x.files, f)
		x.bytes += n
		request.StdinBytes = n
	}
	if err := request.Validate(); err != nil {
		return request, nil, err
	}
	readers := make([]io.Reader, len(x.files))
	for i, f := range x.files {
		readers[i] = f
	}
	// Call 接管这个流并负责关闭；关闭必须能解除读取阻塞，因此只用本地文件。
	files := x.files
	x.files = nil
	return request, &inputStream{Reader: io.MultiReader(readers...), files: files}, nil
}

func (x *execution) receive(o hostexec.Output, r io.Reader) error {
	f, n, err := x.spool(r, o.SizeBytes)
	if err != nil {
		return err
	}
	closeErr := f.Close()
	if n != o.SizeBytes {
		return errors.Join(io.ErrUnexpectedEOF, closeErr)
	}
	if closeErr != nil {
		return closeErr
	}
	x.outputs[o.Path] = f.Name()
	return nil
}

// deliver 只在 Call 完整成功后调用：协议帧正确、尾帧确认、连接正常收尾。
// 在此之前交付产物等于在回收未确认时发布结果。
func (x *execution) deliver(facts Facts, names []string, sink OutputSink) error {
	if sink == nil {
		return nil
	}
	for _, name := range names {
		path, ok := x.outputs[name]
		if !ok {
			continue // helper 未交付该产物；是否算失败由调用方按 Outputs 判断
		}
		f, err := os.Open(path)
		if err != nil {
			return err
		}
		err = sink(facts, name, f)
		if closeErr := f.Close(); err == nil {
			err = closeErr
		}
		if err != nil {
			return err
		}
	}
	return nil
}

func factsOf(r hostexec.Result) Facts {
	f := Facts{ExitCode: r.ExitCode, Signal: r.Signal, CPUNs: r.Usage.CPUNs,
		MemoryBytes: r.Usage.MemoryBytes, ClockNs: r.ClockNs, Reason: r.Reason,
		GroupAccounting: true, OOMKilled: r.Usage.OOM > 0 && r.Usage.OOMKill > 0}
	if r.Cancelled {
		f.Reason = hostexec.ReasonCancelled
	}
	if r.OutputExceeded && f.Reason == "" {
		f.Reason = hostexec.ReasonOutput
	}
	return f
}

func checkResult(r hostexec.Result, j Job) error {
	var err error
	if r.Error != "" {
		err = errors.Join(err, errors.New(r.Error))
	}
	if r.Usage.CPUNs < 0 || r.Usage.MemoryBytes < 0 || r.ClockNs < 0 {
		err = errors.Join(err, fmt.Errorf("helper returned negative resource facts"))
	}
	// 协议帧正确不等于执行组已经清空；残留后代时不能交付成功或复用容量。
	if r.Usage.Populated {
		err = errors.Join(err, fmt.Errorf("helper returned an execution group that was not emptied"))
	}
	if int64(len(r.Stdout)) > j.Limits.StdoutMaxBytes || int64(len(r.Stderr)) > j.Limits.StderrMaxBytes {
		err = errors.Join(err, fmt.Errorf("helper output exceeds the requested limit"))
	}
	return err
}

func writeStreams(j Job, r hostexec.Result) error {
	var err error
	if j.Stdout != nil {
		_, e := j.Stdout.Write(r.Stdout)
		err = errors.Join(err, e)
	}
	if j.Stderr != nil {
		_, e := j.Stderr.Write(r.Stderr)
		err = errors.Join(err, e)
	}
	return err
}

type inputStream struct {
	io.Reader
	files []*os.File
	once  sync.Once
	err   error
}

func (s *inputStream) Close() error {
	s.once.Do(func() {
		for _, f := range s.files {
			s.err = errors.Join(s.err, f.Close())
		}
	})
	return s.err
}
