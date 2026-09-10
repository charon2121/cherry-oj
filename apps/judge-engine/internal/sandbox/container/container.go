package container

import (
	"cherry-oj/judge-engine/internal/contract"
	"context"
	"io"
	"io/fs"
)

type Container interface {
	Start(ctx context.Context, s Spec) (Process, error)
	PutFile(name string, r io.Reader, mode fs.FileMode) error
	GetFile(name string) (io.ReadCloser, error)
	Close() error
}

type Spec struct {
	Command        []string
	Env            []string
	Stdin          io.Reader
	Stdout, Stderr io.Writer
	Limits         contract.Limits
	// Outputs 在 Start 前声明；Wait 确认回收后才能 GetFile。每个 Container 只执行一次。
	Outputs []string
}

type Process interface {
	Wait(ctx context.Context) (Usage, error)
}

type Usage struct {
	ExitCode        int
	Signal          int
	CPUNs           int64
	MemoryBytes     int64
	ClockNs         int64
	Reason          Reason
	OOMKilled       bool
	GroupAccounting bool
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
