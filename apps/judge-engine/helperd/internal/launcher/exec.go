// Package launcher 保存独立 re-exec 进程的可信启动步骤，不能在 HTTP 服务进程内调用。
package launcher

import (
	"fmt"
	"strings"

	"cherry-oj/judge-engine/helperd/internal/policy"
)

// 以下是可信 exec 输入的验证边界，区别于 Request 的客户端预算和
// payloadSpec 实际选择的 rlimit；校验上界不能被误当作实际执行预算。
const (
	invalidLinuxID       = 1<<32 - 1 // Linux UID/GID 的全位 1 保留值。
	maxExecArgs          = 256
	maxExecEnvEntries    = 128
	maxExecStringBytes   = 64 << 10 // 路径字节 + argv/env 各项字节（各项计入结尾 NUL）。
	minExecNoFile        = 8
	maxExecNoFile        = 1024
	maxExecFileSizeBytes = 1 << 30
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
	ReadyFD       int // 0 仅用于原语测试（不握手）；完整启动链使用 ExecReadyFD。
}

func (s ExecSpec) validate() error {
	if s.UID <= 0 || s.GID <= 0 || uint64(s.UID) >= invalidLinuxID || uint64(s.GID) >= invalidLinuxID {
		return fmt.Errorf("payload 必须使用有效的专用非 root UID/GID")
	}
	if s.Path == "" || !strings.HasPrefix(s.Path, "/") || strings.IndexByte(s.Path, 0) >= 0 {
		return fmt.Errorf("payload 路径必须是隔离根内已解析的绝对路径")
	}
	if len(s.Args) == 0 || len(s.Args) > maxExecArgs || len(s.Env) > maxExecEnvEntries {
		return fmt.Errorf("payload 参数/环境条目数无效")
	}
	total := len(s.Path)
	for _, v := range append(append([]string(nil), s.Args...), s.Env...) {
		total += len(v) + 1
		if strings.IndexByte(v, 0) >= 0 {
			return fmt.Errorf("payload 参数/环境不能包含 NUL")
		}
	}
	if total > maxExecStringBytes {
		return fmt.Errorf("payload 参数/环境超过 64KiB")
	}
	if s.NoFile < minExecNoFile || s.NoFile > maxExecNoFile || s.FileSizeBytes == 0 || s.FileSizeBytes > maxExecFileSizeBytes {
		return fmt.Errorf("payload rlimit 超过节点启动器边界")
	}
	if s.ErrorFD < ExtraFilesBaseFD || uint64(s.ErrorFD) >= s.NoFile {
		return fmt.Errorf("payload 错误 FD 无效")
	}
	if s.ReadyFD != 0 && (s.ReadyFD < ExtraFilesBaseFD || uint64(s.ReadyFD) >= s.NoFile || s.ReadyFD == s.ErrorFD) {
		return fmt.Errorf("payload READY FD 无效")
	}
	if s.Profile != policy.Command && s.Profile != policy.Toolchain {
		return fmt.Errorf("payload 策略无效")
	}
	return nil
}
