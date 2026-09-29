package backend

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"syscall"

	"cherry-oj/judge-engine/internal/hostexec"
)

// Executor 通过 setuid 的 sandbox 执行器（apps/sandbox）执行命令。本进程不持有任何特权。
//
// 每次执行占用一个 box：把请求、输入与 stdin 写进 box 目录，运行 `sandbox --box N`，读回一行
// JSON 事实，再从 box 的 out/ 取回 stdout、stderr 与产物。box 目录布局与执行器的约定见
// apps/sandbox/README.md。执行器报告回收未确认时，这个 box 不再归还：残留的进程可能还在，
// 容量只能缩小，不能复用。
type Executor struct {
	binary string
	root   string
	lock   *os.File
	boxes  chan int
}

// 执行器的退出码约定，见 apps/sandbox/README.md。
const (
	executorRefused     = 1
	executorUnreclaimed = 2
)

// NewExecutor 在 root 下独占地建立 count 个 box。root 必须是本服务身份拥有的 0700 目录，
// 执行器以 root 身份只接受属于服务身份的 box。
func NewExecutor(binary, root string, count int) (*Executor, error) {
	if err := checkExecutorBinary(binary); err != nil {
		return nil, err
	}
	return openExecutor(binary, root, count)
}

// checkExecutorBinary 只是启动时的明确报错：执行器没有以 setuid-root 安装时，
// 每次执行都会失败，不如一开始就拒绝启动。
func checkExecutorBinary(binary string) error {
	if !filepath.IsAbs(binary) {
		return fmt.Errorf("the executor path must be absolute: %q", binary)
	}
	info, err := os.Stat(binary)
	if err != nil {
		return err
	}
	stat, ok := info.Sys().(*syscall.Stat_t)
	if !info.Mode().IsRegular() || info.Mode()&os.ModeSetuid == 0 || !ok || stat.Uid != 0 {
		return fmt.Errorf("the executor %s must be a setuid-root regular file", binary)
	}
	return nil
}

func openExecutor(binary, root string, count int) (*Executor, error) {
	if count < 1 || count > 4 {
		return nil, fmt.Errorf("the executor supports 1 to 4 boxes, got %d", count)
	}
	if err := os.MkdirAll(root, 0o700); err != nil {
		return nil, err
	}
	info, err := os.Lstat(root)
	if err != nil {
		return nil, err
	}
	if stat, ok := info.Sys().(*syscall.Stat_t); !info.IsDir() || info.Mode().Perm() != 0o700 || !ok || int(stat.Uid) != os.Geteuid() {
		return nil, fmt.Errorf("the box root must be a 0700 directory owned by the service")
	}
	lock, err := os.OpenFile(filepath.Join(root, ".lock"), os.O_CREATE|os.O_RDWR|syscall.O_NOFOLLOW, 0o600)
	if err != nil {
		return nil, err
	}
	if err = syscall.Flock(int(lock.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil {
		lock.Close()
		return nil, fmt.Errorf("the box root is in use by another process: %w", err)
	}
	e := &Executor{binary: binary, root: root, lock: lock, boxes: make(chan int, count)}
	for i := 0; i < count; i++ {
		if err := e.reset(i); err != nil {
			lock.Close()
			return nil, err
		}
		e.boxes <- i
	}
	return e, nil
}

// Close 释放 box 根的独占锁，必须在所有执行结束之后调用。
func (e *Executor) Close() error { return e.lock.Close() }

func (e *Executor) boxDir(i int) string { return filepath.Join(e.root, strconv.Itoa(i)) }

// reset 把 box 恢复成空的初始状态。上一次执行留下的产物只属于上一次。
func (e *Executor) reset(i int) error {
	dir := e.boxDir(i)
	if err := os.RemoveAll(dir); err != nil {
		return err
	}
	for _, d := range []string{dir, filepath.Join(dir, "in"), filepath.Join(dir, "out")} {
		if err := os.Mkdir(d, 0o700); err != nil {
			return err
		}
	}
	return nil
}

func (e *Executor) Execute(ctx context.Context, j Job, sink OutputSink) (Facts, error) {
	var box int
	select {
	case box = <-e.boxes:
	case <-ctx.Done():
		return Facts{}, ctx.Err()
	}
	facts, reclaimed, err := e.execute(ctx, box, j, sink)
	if reclaimed {
		e.boxes <- box
	}
	return facts, err
}

func (e *Executor) execute(ctx context.Context, box int, j Job, sink OutputSink) (Facts, bool, error) {
	if err := e.reset(box); err != nil {
		return Facts{}, false, cleanupFailed("reset box %d: %w", box, err)
	}
	dir := e.boxDir(box)
	if err := writeRequest(dir, j); err != nil {
		return Facts{}, true, err
	}
	out, code, err := e.run(ctx, box)
	switch {
	case err != nil:
		// 执行器没能按约定退出（被信号杀死等），执行组可能残留，只能按回收未确认处理。
		return Facts{}, false, cleanupFailed("sandbox executor: %w", err)
	case code == executorUnreclaimed:
		return Facts{}, false, cleanupFailed("sandbox executor could not reclaim box %d: %s", box, out.stderr)
	case code == executorRefused:
		return Facts{}, true, fmt.Errorf("sandbox executor refused the request: %s", out.stderr)
	case code != 0:
		return Facts{}, false, cleanupFailed("sandbox executor exited with unexpected code %d", code)
	}
	result, err := parseResult(out.stdout)
	if err != nil {
		return Facts{}, true, err
	}
	facts := result.facts()
	if result.Error != "" {
		return facts, true, errors.New(result.Error)
	}
	if err := deliverStreams(dir, j, result); err != nil {
		return facts, true, err
	}
	return facts, true, deliverOutputs(dir, j, result, facts, sink)
}

type executorOutput struct {
	stdout []byte
	stderr string
}

// run 运行执行器，并在 stdin 上接一根取消管道：ctx 取消时关闭写端，执行器随即停组并如实报告。
// 不直接杀死执行器——那样它来不及回收执行组。
func (e *Executor) run(ctx context.Context, box int) (executorOutput, int, error) {
	read, write, err := os.Pipe()
	if err != nil {
		return executorOutput{}, 0, err
	}
	var stdout, stderr limitedBuffer
	stdout.max, stderr.max = 64<<10, 4<<10
	cmd := exec.Command(e.binary, "--box", strconv.Itoa(box))
	cmd.Stdin, cmd.Stdout, cmd.Stderr, cmd.Env = read, &stdout, &stderr, []string{}
	err = cmd.Start()
	read.Close()
	if err != nil {
		write.Close()
		return executorOutput{}, 0, err
	}
	var once sync.Once
	cancel := func() { once.Do(func() { write.Close() }) }
	done := make(chan struct{})
	go func() {
		select {
		case <-ctx.Done():
			cancel()
		case <-done:
		}
	}()
	err = cmd.Wait()
	close(done)
	cancel()
	out := executorOutput{stdout: stdout.Bytes(), stderr: strings.TrimSpace(stderr.String())}
	var exit *exec.ExitError
	if errors.As(err, &exit) && exit.ExitCode() >= 0 {
		return out, exit.ExitCode(), nil
	}
	return out, 0, err
}

// writeRequest 按执行器的请求格式写 box：NUL 结尾的 key=value 记录，输入按序号放进 in/。
func writeRequest(dir string, j Job) error {
	var spec bytes.Buffer
	record := func(k, v string) error {
		if strings.ContainsRune(v, 0) {
			return fmt.Errorf("%s contains NUL", k)
		}
		spec.WriteString(k + "=" + v + "\x00")
		return nil
	}
	for _, a := range j.Command {
		if err := record("arg", a); err != nil {
			return err
		}
	}
	for _, v := range j.Env {
		if err := record("env", v); err != nil {
			return err
		}
	}
	var total int64
	for i, in := range j.Inputs {
		if !hostexec.ValidPath(in.Name) {
			return fmt.Errorf("invalid input path: %q", in.Name)
		}
		n, err := spool(filepath.Join(dir, "in", strconv.Itoa(i)), in.Reader, hostexec.MaxInputBytes-total)
		if err != nil {
			return err
		}
		total += n
		flag := "0"
		if in.Executable {
			flag = "1"
		}
		spec.WriteString("input=" + flag + ":" + in.Name + "\x00")
	}
	var stdin io.Reader = strings.NewReader("")
	if j.Stdin != nil {
		stdin = j.Stdin.Reader
	}
	if _, err := spool(filepath.Join(dir, "stdin"), stdin, hostexec.MaxInputBytes-total); err != nil {
		return err
	}
	for _, o := range j.Outputs {
		if !hostexec.ValidPath(o) {
			return fmt.Errorf("invalid artifact path: %q", o)
		}
		spec.WriteString("output=" + o + "\x00")
	}
	l := j.Limits
	for _, kv := range []struct {
		k string
		v int64
	}{{"cpu_ns", l.CPUNs}, {"clock_ns", l.ClockNs}, {"memory_bytes", l.MemoryBytes},
		{"max_processes", int64(l.MaxProcesses)}, {"stdout_max_bytes", l.StdoutMaxBytes},
		{"stderr_max_bytes", l.StderrMaxBytes}} {
		spec.WriteString(kv.k + "=" + strconv.FormatInt(kv.v, 10) + "\x00")
	}
	return os.WriteFile(filepath.Join(dir, "spec"), spec.Bytes(), 0o600)
}

// spool 写入不超过 limit 字节；limit+1 用于发现超限，不能在上限处伪装 EOF，
// 否则被截断的源码会被当作完整输入交付。
func spool(path string, r io.Reader, limit int64) (int64, error) {
	if r == nil {
		return 0, fmt.Errorf("the input stream is nil")
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY|syscall.O_NOFOLLOW, 0o600)
	if err != nil {
		return 0, err
	}
	n, err := io.Copy(f, io.LimitReader(r, limit+1))
	if err == nil && n > limit {
		err = fmt.Errorf("total file size exceeds %d bytes", limit)
	}
	return n, errors.Join(err, f.Close())
}

// executorResult 是执行器输出的一行 JSON；字段名即约定，改名等于改执行器接口。
type executorResult struct {
	Version         int                `json:"version"`
	ExitCode        int                `json:"exitCode"`
	Signal          int                `json:"signal"`
	CPUNs           int64              `json:"cpuNs"`
	MemoryBytes     int64              `json:"memoryBytes"`
	ClockNs         int64              `json:"clockNs"`
	Reason          hostexec.Reason    `json:"reason"`
	OOM             uint64             `json:"oom"`
	OOMKill         uint64             `json:"oomKill"`
	MemoryMaxEvents uint64             `json:"memoryMaxEvents"`
	PidsMaxEvents   uint64             `json:"pidsMaxEvents"`
	Cancelled       bool               `json:"cancelled"`
	OutputExceeded  bool               `json:"outputExceeded"`
	StdoutBytes     int64              `json:"stdoutBytes"`
	StderrBytes     int64              `json:"stderrBytes"`
	Outputs         []executorArtifact `json:"outputs"`
	Error           string             `json:"error"`
}

type executorArtifact struct {
	Index     int    `json:"index"`
	Path      string `json:"path"`
	SizeBytes int64  `json:"sizeBytes"`
}

func parseResult(data []byte) (executorResult, error) {
	var r executorResult
	d := json.NewDecoder(bytes.NewReader(data))
	d.DisallowUnknownFields()
	if err := d.Decode(&r); err != nil {
		return r, fmt.Errorf("sandbox executor returned invalid facts: %w", err)
	}
	if d.More() {
		return r, fmt.Errorf("sandbox executor returned trailing data")
	}
	known := r.Reason == ""
	for _, reason := range hostexec.AllReasons() {
		known = known || r.Reason == reason
	}
	if r.Version != 1 || !known || r.CPUNs < 0 || r.MemoryBytes < 0 || r.ClockNs < 0 || r.StdoutBytes < 0 || r.StderrBytes < 0 {
		return r, fmt.Errorf("sandbox executor returned malformed facts")
	}
	return r, nil
}

func (r executorResult) facts() Facts {
	f := Facts{ExitCode: r.ExitCode, Signal: r.Signal, CPUNs: r.CPUNs, MemoryBytes: r.MemoryBytes,
		ClockNs: r.ClockNs, Reason: r.Reason, GroupAccounting: true, OOMKilled: r.OOM > 0 && r.OOMKill > 0}
	if r.Cancelled {
		f.Reason = hostexec.ReasonCancelled
	}
	if r.OutputExceeded && f.Reason == "" {
		f.Reason = hostexec.ReasonOutput
	}
	return f
}

// deliverStreams 把 out/stdout 与 out/stderr 交给调用方；长度必须与事实一致且不超过请求的上限。
func deliverStreams(dir string, j Job, r executorResult) error {
	for _, s := range []struct {
		name  string
		size  int64
		limit int64
		w     io.Writer
	}{{"stdout", r.StdoutBytes, j.Limits.StdoutMaxBytes, j.Stdout}, {"stderr", r.StderrBytes, j.Limits.StderrMaxBytes, j.Stderr}} {
		if s.size > s.limit {
			return fmt.Errorf("sandbox executor %s exceeds the requested limit", s.name)
		}
		data, err := os.ReadFile(filepath.Join(dir, "out", s.name))
		if err != nil {
			return err
		}
		if int64(len(data)) != s.size {
			return fmt.Errorf("sandbox executor %s length differs from its facts", s.name)
		}
		if s.w != nil {
			if _, err := s.w.Write(data); err != nil {
				return err
			}
		}
	}
	return nil
}

// deliverOutputs 逐个交付产物。产物必须是这次请求声明过的，长度与事实一致。
func deliverOutputs(dir string, j Job, r executorResult, facts Facts, sink OutputSink) error {
	var total int64
	for _, o := range r.Outputs {
		if o.Index < 0 || o.Index >= len(j.Outputs) || j.Outputs[o.Index] != o.Path || o.SizeBytes < 0 || o.SizeBytes > hostexec.MaxArtifactBytes-total {
			return fmt.Errorf("sandbox executor delivered an unrequested or oversized artifact: %q", o.Path)
		}
		total += o.SizeBytes
		if sink == nil {
			continue
		}
		if err := deliverOutput(filepath.Join(dir, "out", "artifact-"+strconv.Itoa(o.Index)), o, facts, sink); err != nil {
			return err
		}
	}
	return nil
}

func deliverOutput(path string, o executorArtifact, facts Facts, sink OutputSink) error {
	f, err := os.OpenFile(path, os.O_RDONLY|syscall.O_NOFOLLOW, 0)
	if err != nil {
		return err
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() || info.Size() != o.SizeBytes {
		return fmt.Errorf("sandbox executor artifact %q does not match its facts", o.Path)
	}
	return sink(facts, o.Path, io.LimitReader(f, o.SizeBytes))
}

// limitedBuffer 只保留前 max 字节，防止执行器异常输出撑大内存。
type limitedBuffer struct {
	bytes.Buffer
	max int
}

func (b *limitedBuffer) Write(p []byte) (int, error) {
	if room := b.max - b.Len(); room > 0 {
		b.Buffer.Write(p[:min(len(p), room)])
	}
	return len(p), nil
}
