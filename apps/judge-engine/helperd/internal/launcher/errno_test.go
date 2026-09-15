// White-box check of the fixed-size terminal record's errno conversion.
package launcher

import (
	"errors"
	"fmt"
	"syscall"
	"testing"
)

func TestFailureErrnoPreservesCause(t *testing.T) {
	for _, errno := range []syscall.Errno{syscall.EPERM, syscall.ENOSYS, syscall.EINVAL} {
		if got := failureErrno(fmt.Errorf("operation: %w", errno)); got != errno {
			t.Fatalf("got %v want %v", got, errno)
		}
	}
	if failureErrno(errors.New("TSYNC mismatch")) != 0 {
		t.Fatal("invented errno")
	}
}
