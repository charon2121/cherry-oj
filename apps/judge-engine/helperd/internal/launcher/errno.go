//go:build linux && amd64

package launcher

import (
	"errors"
	"syscall"
)

// 非 syscall 错误（如 TSYNC 未覆盖全部线程）没有 errno，返回零保留这一事实；
// 失败仍由阶段记录表达，不能凭猜测填成权限错误。
func failureErrno(err error) syscall.Errno {
	var errno syscall.Errno
	if errors.As(err, &errno) {
		return errno
	}
	return 0
}
