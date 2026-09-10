package launcher

import (
	"errors"
	"syscall"
)

// A non-syscall failure (for example a TSYNC thread mismatch) has errno zero;
// the terminal stage still marks failure. Never invent a permission error.
func failureErrno(err error) syscall.Errno {
	var errno syscall.Errno
	if errors.As(err, &errno) {
		return errno
	}
	return 0
}
