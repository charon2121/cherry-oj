// Package helper 是本机特权执行边界，只返回进程和资源事实，不理解判题。
package helper

import (
	"fmt"
	"path/filepath"

	"cherry-oj/judge-engine/internal/sandbox/cgroup"
)

type Config struct {
	SocketPath, StateDir, JobsDir, RootFS, ManifestPath, ManifestSHA256 string
	ServiceUID, ServiceGID, PayloadUID, PayloadGID, InitUID, InitGID    int
	Parallelism                                                         int
}

func (c Config) Validate() error {
	for _, p := range []string{c.SocketPath, c.StateDir, c.JobsDir, c.RootFS, c.ManifestPath} {
		if !filepath.IsAbs(p) || filepath.Clean(p) != p || p == "/" {
			return fmt.Errorf("helper 路径必须为明确的绝对路径")
		}
	}
	if filepath.Dir(c.SocketPath) != c.StateDir {
		return fmt.Errorf("socket 必须位于独占 state 目录")
	}
	if c.ServiceGID <= 0 || c.ServiceUID <= 0 || c.PayloadUID <= 0 || c.PayloadGID <= 0 || c.InitUID <= 0 || c.InitGID <= 0 || c.ServiceUID == c.PayloadUID || c.ServiceUID == c.InitUID || c.PayloadUID == c.InitUID || c.PayloadGID == c.InitGID || c.ServiceGID == c.PayloadGID || c.ServiceGID == c.InitGID {
		return fmt.Errorf("服务、init、payload 必须使用分离的非 root 身份")
	}
	for _, id := range []int{c.ServiceUID, c.ServiceGID, c.PayloadUID, c.PayloadGID, c.InitUID, c.InitGID} {
		if uint64(id) >= 1<<32-1 {
			return fmt.Errorf("身份越界")
		}
	}
	if c.Parallelism < 1 || c.Parallelism > 4 {
		return fmt.Errorf("并发必须为 1～4")
	}
	if err := c.validateSlotIdentities(); err != nil {
		return err
	}
	if len(c.ManifestSHA256) != 64 {
		return fmt.Errorf("必须固定 rootfs manifest 摘要")
	}
	return nil
}

// Completion 仅在所有产物 FD 已关闭后发送；缺失时客户端不能把部分交付当作成功。
type Completion struct {
	Version  int
	Complete bool
}

type Output struct {
	Path      string
	SizeBytes int64
}
type Result struct {
	Cancelled        bool
	OutputExceeded   bool
	Version          int
	ExitCode, Signal int
	Usage            cgroup.Snapshot
	ClockNs          int64
	Reason           Reason // cpu / wall / output / cancelled / platform；不从 SIGKILL 猜测原因。
	Stdout, Stderr   []byte
	Outputs          []Output
	Error            string
}

// boundedCapture 持续 drain，但只保存上限内的字节。溢出通知不能堵住 stdout/stderr。
type boundedCapture struct {
	data     []byte
	limit    int64
	overflow chan<- struct{}
	exceeded bool
}

func (w *boundedCapture) Write(b []byte) (int, error) {
	n := len(b)
	remaining := w.limit - int64(len(w.data))
	keep := int64(n)
	if keep > remaining {
		keep = remaining
		w.exceeded = true
		select {
		case w.overflow <- struct{}{}:
		default:
		}
	}
	w.data = append(w.data, b[:int(keep)]...)
	return n, nil
}

// Reason 是主动终止或平台故障原因，不替代退出信号和 cgroup 资源事实。
type Reason string

const (
	ReasonCPU       Reason = "cpu"
	ReasonWall      Reason = "wall"
	ReasonOutput    Reason = "output"
	ReasonCancelled Reason = "cancelled"
	ReasonPlatform  Reason = "platform"
)
