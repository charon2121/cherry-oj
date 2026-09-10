// Package launcher 保存独立 re-exec 进程的可信启动步骤，不能在 HTTP 服务进程内调用。
package launcher

import (
	"fmt"
	"strings"

	"cherry-oj/judge-engine/internal/sandbox/policy"
)

// ExecSpec 仅由受信 helper 生成，路径必须已在独立 rootfs 中解析。
// 它不是公开 RunSpec：客户端不能选择 UID、策略或错误通道 FD。
type ExecSpec struct {
	Path          string
	Args          []string
	Env           []string
	UID           int
	GID           int
	NoFile        uint64
	FileSizeBytes uint64
	Profile       policy.Profile
	ErrorFD       int
	ReadyFD       int // 0 仅用于原语测试；完整启动链固定为 4。
}

func (s ExecSpec) validate() error {
	if s.UID <= 0 || s.GID <= 0 || uint64(s.UID) >= 1<<32-1 || uint64(s.GID) >= 1<<32-1 {
		return fmt.Errorf("payload 必须使用有效的专用非 root UID/GID")
	}
	if s.Path == "" || !strings.HasPrefix(s.Path, "/") || strings.IndexByte(s.Path, 0) >= 0 {
		return fmt.Errorf("payload 路径必须是隔离根内已解析的绝对路径")
	}
	if len(s.Args) == 0 || len(s.Args) > 256 || len(s.Env) > 128 {
		return fmt.Errorf("payload 参数/环境条目数无效")
	}
	total := len(s.Path)
	for _, v := range append(append([]string(nil), s.Args...), s.Env...) {
		total += len(v) + 1
		if strings.IndexByte(v, 0) >= 0 {
			return fmt.Errorf("payload 参数/环境不能包含 NUL")
		}
	}
	if total > 64<<10 {
		return fmt.Errorf("payload 参数/环境超过 64KiB")
	}
	if s.NoFile < 8 || s.NoFile > 1024 || s.FileSizeBytes == 0 || s.FileSizeBytes > 1<<30 {
		return fmt.Errorf("payload rlimit 超过节点启动器边界")
	}
	if s.ErrorFD < 3 || uint64(s.ErrorFD) >= s.NoFile {
		return fmt.Errorf("payload 错误 FD 无效")
	}
	if s.ReadyFD != 0 && (s.ReadyFD < 3 || uint64(s.ReadyFD) >= s.NoFile || s.ReadyFD == s.ErrorFD) {
		return fmt.Errorf("payload READY FD 无效")
	}
	if s.Profile != policy.Command && s.Profile != policy.Toolchain {
		return fmt.Errorf("payload 策略无效")
	}
	return nil
}
