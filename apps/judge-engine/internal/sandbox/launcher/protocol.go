package launcher

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"regexp"
	"strings"

	"cherry-oj/judge-engine/internal/contract"
)

const Version = 1
const MaxFrameBytes = 64 << 10
const MaxInputBytes int64 = 64 << 20
const MaxArtifactBytes int64 = 64 << 20

// Request 是仅供本机 Go 服务调用的内部协议，不包含任何宿主路径或特权配置。
// Inputs 后接 StdinBytes 个标准输入字节；数据不放进 JSON，避免 base64 全量缓冲。
type Input struct {
	Path       string
	SizeBytes  int64
	Executable bool
}
type Request struct {
	Version      int
	Command, Env []string
	Inputs       []Input
	StdinBytes   int64
	Outputs      []string
	Limits       contract.Limits
}

var commandName = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_.+-]{0,127}$`)

var segment = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$`)

func ValidPath(name string) bool {
	parts := strings.Split(name, "/")
	if len(parts) > 8 {
		return false
	}
	for _, p := range parts {
		if !segment.MatchString(p) {
			return false
		}
	}
	return true
}
func (r Request) InputBytes() int64 {
	n := r.StdinBytes
	for _, f := range r.Inputs {
		n += f.SizeBytes
	}
	return n
}
func (r Request) Validate() error {
	if r.Version != Version || len(r.Command) == 0 || len(r.Command) > 256 || len(r.Env) > 128 || len(r.Inputs) > 128 || len(r.Outputs) > 128 {
		return fmt.Errorf("无效版本或条目数")
	}
	// 命令先在工作区，再在只读 rootfs 固定目录中解析；请求不能提供绝对路径。
	if !commandName.MatchString(r.Command[0]) {
		return fmt.Errorf("命令必须为裸名称")
	}
	total := 0
	for _, list := range [][]string{r.Command, r.Env} {
		for _, s := range list {
			total += len(s) + 1
			if strings.ContainsRune(s, 0) {
				return fmt.Errorf("参数包含 NUL")
			}
		}
	}
	if total > 32<<10 {
		return fmt.Errorf("参数/环境过大")
	}
	for _, e := range r.Env {
		key, _, ok := strings.Cut(e, "=")
		if !ok || key == "" {
			return fmt.Errorf("环境变量格式错误")
		}
	}
	if r.StdinBytes < 0 || r.StdinBytes > MaxInputBytes {
		return fmt.Errorf("stdin 大小无效")
	}
	n := r.StdinBytes
	seen := map[string]bool{}
	for _, f := range r.Inputs {
		if !ValidPath(f.Path) || seen[f.Path] || f.SizeBytes < 0 || f.SizeBytes > MaxInputBytes-n {
			return fmt.Errorf("输入路径/大小无效")
		}
		n += f.SizeBytes
		seen[f.Path] = true
	}
	seen = map[string]bool{}
	for _, p := range r.Outputs {
		if !ValidPath(p) || seen[p] {
			return fmt.Errorf("产物路径无效")
		}
		seen[p] = true
	}
	if err := r.Limits.Validate(); err != nil {
		return err
	}
	// 本机调用者必须已经完成默认值填充。零 CPU/内存由上层返回资源结论，不启动 helper。
	l := r.Limits
	if l.CPUNs <= 0 || l.ClockNs <= 0 || l.MemoryBytes <= 0 || l.MaxProcesses <= 0 || l.CPUNs > 60_000_000_000 || l.ClockNs > 120_000_000_000 || l.MemoryBytes > 1<<30 || l.MaxProcesses > 256 || l.StdoutMaxBytes > 1<<20 || l.StderrMaxBytes > 1<<20 {
		return fmt.Errorf("执行限额未归一化或超出节点硬边界")
	}
	return nil
}

// ReadFrame 严格限制分配大小，拒绝未知字段与尾随 JSON；不缓冲后续文件字节流。
func ReadFrame(r io.Reader, v any, max uint32) error {
	var h [4]byte
	if _, err := io.ReadFull(r, h[:]); err != nil {
		return err
	}
	n := binary.BigEndian.Uint32(h[:])
	if n == 0 || n > max {
		return fmt.Errorf("控制帧大小无效")
	}
	lr := &io.LimitedReader{R: r, N: int64(n)}
	d := json.NewDecoder(lr)
	d.DisallowUnknownFields()
	if err := d.Decode(v); err != nil {
		return err
	}
	var extra any
	if err := d.Decode(&extra); err != io.EOF {
		return fmt.Errorf("控制帧有尾随内容")
	}
	if lr.N != 0 {
		return io.ErrUnexpectedEOF
	}
	return nil
}
func WriteFrame(w io.Writer, v any, max uint32) error {
	b, err := json.Marshal(v)
	if err != nil {
		return err
	}
	if len(b) == 0 || len(b) > int(max) {
		return fmt.Errorf("控制帧过大")
	}
	var h [4]byte
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

// StageSpec 只通过 helper 创建的匿名管道传给可信 init，绝不从 socket 客户端解码。
type StageSpec struct {
	Request                                  Request
	RootFS, MountPoint, Executable           string
	PayloadUID, PayloadGID, InitUID, InitGID int
	WorkspaceBytes                           int64
	WorkspaceInodes                          int
}

type Event struct {
	Errno            uint32 // 可信 init 失败的原始 errno；最终 exec 使用 ExecErrno。
	Phase            string
	ExecStage        byte
	ExecErrno        uint32
	Version          int
	Kind             string
	ExitCode, Signal int
	ExecFailed       bool
}
