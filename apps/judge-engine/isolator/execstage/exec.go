//go:build linux && amd64

// Package execstage 是 P5：降权、安装 seccomp、完成 READY/GO 握手后 execve 用户命令。
package execstage

import (
	"fmt"
	"strings"

	"cherry-oj/judge-engine/isolator/seccomp"
	"cherry-oj/judge-engine/isolator/startup"
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

// validate 是 P5 对收到的 ExecSpec 做的最后一道检查，不信任 P4 已经校验过。
func validate(s startup.ExecSpec) error {
	if s.UID <= 0 || s.GID <= 0 || uint64(s.UID) >= invalidLinuxID || uint64(s.GID) >= invalidLinuxID {
		return fmt.Errorf("payload must use a valid dedicated non-root UID/GID")
	}
	if s.Path == "" || !strings.HasPrefix(s.Path, "/") || strings.IndexByte(s.Path, 0) >= 0 {
		return fmt.Errorf("payload path must be a resolved absolute path inside the isolation root")
	}
	if len(s.Args) == 0 || len(s.Args) > maxExecArgs || len(s.Env) > maxExecEnvEntries {
		return fmt.Errorf("invalid payload argument/environment entry count")
	}
	total := len(s.Path)
	for _, v := range append(append([]string(nil), s.Args...), s.Env...) {
		total += len(v) + 1
		if strings.IndexByte(v, 0) >= 0 {
			return fmt.Errorf("payload arguments/environment must not contain NUL")
		}
	}
	if total > maxExecStringBytes {
		return fmt.Errorf("payload arguments/environment exceed 64KiB")
	}
	if s.NoFile < minExecNoFile || s.NoFile > maxExecNoFile || s.FileSizeBytes == 0 || s.FileSizeBytes > maxExecFileSizeBytes {
		return fmt.Errorf("payload rlimit exceeds the node launcher boundary")
	}
	if s.ErrorFD < startup.ExtraFilesBaseFD || uint64(s.ErrorFD) >= s.NoFile {
		return fmt.Errorf("invalid payload error FD")
	}
	if s.ReadyFD != 0 && (s.ReadyFD < startup.ExtraFilesBaseFD || uint64(s.ReadyFD) >= s.NoFile || s.ReadyFD == s.ErrorFD) {
		return fmt.Errorf("invalid payload READY FD")
	}
	if s.Profile != seccomp.Command && s.Profile != seccomp.Toolchain {
		return fmt.Errorf("invalid payload policy")
	}
	return nil
}
