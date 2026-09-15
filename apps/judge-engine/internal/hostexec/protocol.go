// Package hostexec 定义非特权 sandbox 与特权 helper 之间的本机执行协议。
//
// 它是两端唯一的共享词汇：请求、响应、终止原因与帧编解码只在此定义一次。
// 本包不实现特权操作，也不理解判题；helper 的服务端实现与 sandbox 的客户端
// 分别位于各自的包中，对同一份协议编程。
package hostexec

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"regexp"
	"strings"
	"time"

	"cherry-oj/judge-engine/internal/contract"
)

const Version = 1

// 本机请求先限制条目及累计字节，避免客户端用小控制帧触发无界资源分配。
// 输入与产物是两个独立累计预算；结果帧预算见 result.go。
const (
	MaxFrameBytes               = 64 << 10
	MaxInputBytes         int64 = 64 << 20
	MaxArtifactBytes      int64 = 64 << 20
	MaxOutputs                  = 128 // 请求声明及 helper 响应允许的产物数量。
	maxRequestInputs            = 128
	maxRequestArgs              = 256
	maxRequestEnvEntries        = 128
	maxRequestStringBytes       = 32 << 10 // argv/env，包括各字符串结尾 NUL。
	maxPathSegments             = 8
	frameHeaderBytes            = 4 // uint32 大端长度；后面紧接 JSON，再后面可跟文件流。
)

// 已归一化请求的节点硬边界，不是 runner 填充的默认限额。
const (
	maxCPUNs = int64(60 * time.Second)
	// MaxClockNs 是单次执行的墙钟硬界。它是跨层预算的下界：本机会话期限必须大于它，
	// HTTP 写期限又必须大于会话期限，否则达到墙钟上限的命令会先被上层期限打断。
	MaxClockNs     = int64(120 * time.Second)
	maxClockNs     = MaxClockNs
	maxMemoryBytes = 1 << 30
	maxProcesses   = 256
	maxStdoutBytes = 1 << 20
	maxStderrBytes = 1 << 20
)

// Input 的 Path 是工作区逻辑路径；SizeBytes 用来划分随请求发送的连续文件流。
// 不允许调用者指定宿主路径或文件所有者。
type Input struct {
	Path       string
	SizeBytes  int64
	Executable bool
}

// Request 是本机非特权服务发给 helper 的协议，不接受宿主路径或特权设置。
// 控制帧后依 Inputs 顺序发送文件，最后发送 StdinBytes 个 stdin 字节；
// 文件不放进 JSON，避免 base64 造成全量内存缓冲。
type Request struct {
	Version      int
	Command, Env []string
	Inputs       []Input
	StdinBytes   int64
	Outputs      []string
	Limits       contract.Limits
}

// 名称最多 128 个 ASCII 字节：首字符一位，后续最多 127 位；两种字符集有意不同。
var commandName = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_.+-]{0,127}$`)

var segment = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$`)

// ValidPath 只接受工作区相对名称，排除空段、点段和保留的点前缀内部文件。
// 它不能替代打开文件时的链接/挂载边界校验。
func ValidPath(name string) bool {
	parts := strings.Split(name, "/")
	if len(parts) > maxPathSegments {
		return false
	}
	for _, p := range parts {
		if !segment.MatchString(p) {
			return false
		}
	}
	return true
}

// InputBytes 用于划分控制帧后的数据区，调用前必须 Validate 以确保总量未溢出。
func (r Request) InputBytes() int64 {
	n := r.StdinBytes
	for _, f := range r.Inputs {
		n += f.SizeBytes
	}
	return n
}

// Validate 不填充默认值；helper 不能自行猜测显式零预算的含义。
// 请求须由 runner 归一化后交付，特权边界仍独立复查，不能只信客户端校验。
func (r Request) Validate() error {
	if r.Version != Version || len(r.Command) == 0 || len(r.Command) > maxRequestArgs || len(r.Env) > maxRequestEnvEntries || len(r.Inputs) > maxRequestInputs || len(r.Outputs) > MaxOutputs {
		return fmt.Errorf("invalid version or entry count")
	}
	// 命令先在工作区，再在只读 rootfs 固定目录中解析；请求不能提供绝对路径。
	if !commandName.MatchString(r.Command[0]) {
		return fmt.Errorf("command must be a bare name")
	}
	total := 0
	for _, list := range [][]string{r.Command, r.Env} {
		for _, s := range list {
			total += len(s) + 1
			if strings.ContainsRune(s, 0) {
				return fmt.Errorf("arguments contain NUL")
			}
		}
	}
	if total > maxRequestStringBytes {
		return fmt.Errorf("arguments/environment are too large")
	}
	for _, e := range r.Env {
		key, _, ok := strings.Cut(e, "=")
		if !ok || key == "" {
			return fmt.Errorf("malformed environment variable")
		}
	}
	if r.StdinBytes < 0 || r.StdinBytes > MaxInputBytes {
		return fmt.Errorf("invalid stdin size")
	}
	n := r.StdinBytes
	seen := map[string]bool{}
	for _, f := range r.Inputs {
		if !ValidPath(f.Path) || seen[f.Path] || f.SizeBytes < 0 || f.SizeBytes > MaxInputBytes-n {
			return fmt.Errorf("invalid input path or size")
		}
		n += f.SizeBytes
		seen[f.Path] = true
	}
	seen = map[string]bool{}
	for _, p := range r.Outputs {
		if !ValidPath(p) || seen[p] {
			return fmt.Errorf("invalid artifact path")
		}
		seen[p] = true
	}
	if err := r.Limits.Validate(); err != nil {
		return err
	}
	// 本机调用者必须已经完成默认值填充。零 CPU/内存由上层返回资源结论，不启动 helper。
	l := r.Limits
	if l.CPUNs <= 0 || l.ClockNs <= 0 || l.MemoryBytes <= 0 || l.MaxProcesses <= 0 || l.CPUNs > maxCPUNs || l.ClockNs > maxClockNs || l.MemoryBytes > maxMemoryBytes || l.MaxProcesses > maxProcesses || l.StdoutMaxBytes > maxStdoutBytes || l.StderrMaxBytes > maxStderrBytes {
		return fmt.Errorf("execution limits are not normalized or exceed the node hard boundary")
	}
	return nil
}

// ReadFrame 严格限制分配大小，拒绝未知字段与尾随 JSON；不缓冲后续文件字节流。
func ReadFrame(r io.Reader, v any, max uint32) error {
	var h [frameHeaderBytes]byte
	if _, err := io.ReadFull(r, h[:]); err != nil {
		return err
	}
	n := binary.BigEndian.Uint32(h[:])
	if n == 0 || n > max {
		return fmt.Errorf("invalid control frame size")
	}
	lr := &io.LimitedReader{R: r, N: int64(n)}
	d := json.NewDecoder(lr)
	d.DisallowUnknownFields()
	if err := d.Decode(v); err != nil {
		return err
	}
	var extra any
	if err := d.Decode(&extra); err != io.EOF {
		return fmt.Errorf("control frame has trailing content")
	}
	if lr.N != 0 {
		return io.ErrUnexpectedEOF
	}
	return nil
}

// WriteFrame 使用与 ReadFrame 相同的长度前缀；短写也必须处理完，避免后续文件流错位。
func WriteFrame(w io.Writer, v any, max uint32) error {
	b, err := json.Marshal(v)
	if err != nil {
		return err
	}
	if len(b) == 0 || len(b) > int(max) {
		return fmt.Errorf("control frame is too large")
	}
	var h [frameHeaderBytes]byte
	binary.BigEndian.PutUint32(h[:], uint32(len(b)))
	if err = writeAll(w, h[:]); err != nil {
		return err
	}
	return writeAll(w, b)
}
func writeAll(w io.Writer, b []byte) error {
	for len(b) > 0 {
		n, e := w.Write(b)
		if e != nil {
			return e
		}
		if n == 0 {
			return io.ErrShortWrite
		}
		b = b[n:]
	}
	return nil
}
