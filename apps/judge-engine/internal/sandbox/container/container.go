package container

import (
	"context"
	"io"
	"io/fs"

	"cherry-oj/judge-engine/internal/contract"
)

// Container 是单次执行的工作区：先 PutFile，调用一次 Start，Wait 后读取产物，最后 Close。
// Close 是最终回收边界；调用者不能在它成功前对外确认结果。
type Container interface {
	// Start 返回执行句柄；异步后端仍可能在之后才发现启动失败，必须继续 Wait。
	Start(ctx context.Context, s Spec) (Process, error)
	// PutFile 同步消费 r，但不接管 r.Close；输入句柄仍由调用者释放。
	PutFile(name string, r io.Reader, mode fs.FileMode) error
	// GetFile 返回的 reader 由调用者关闭，且必须在 Close 工作区前读完。
	GetFile(name string) (io.ReadCloser, error)
	// Close 取消并等待在途执行后释放工作区，可重复调用并保留回收错误。
	Close() error
}

// Spec 使用已归一化的 Limits；输入输出流必须在执行等待期间保持可用。
type Spec struct {
	Command        []string
	Env            []string
	Stdin          io.Reader
	Stdout, Stderr io.Writer
	Limits         contract.Limits
	// Outputs 在 Start 前声明，限制 helper 可交付的文件范围；不是宿主文件路径。
	Outputs []string
}

// Process 区分命令执行结束与 Container 的工作区释放。
type Process interface {
	// Wait 即使因 ctx 取消也要完成后端收尾；返回 Usage 前，后端不能继续写输出。
	Wait(ctx context.Context) (Usage, error)
}

// Usage 是执行事实，不直接给出判题结论。
type Usage struct {
	ExitCode        int
	Signal          int
	CPUNs           int64
	MemoryBytes     int64
	ClockNs         int64
	Reason          Reason
	OOMKilled       bool // Linux 后端须同时有本任务 oom 与 oom_kill 证据，SIGKILL 本身不够。
	GroupAccounting bool // 标识资源来自整组计量，runner 据此避免套用 host 的峰值推断。
}

// Reason 只表达执行事实，不携带判题状态。未知原因必须作为平台错误处理。
type Reason string

const (
	ReasonCPU       Reason = "cpu"
	ReasonWall      Reason = "wall"
	ReasonOutput    Reason = "output"
	ReasonCancelled Reason = "cancelled"
	ReasonPlatform  Reason = "platform"
)
