package pool_test

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/backend"
	"cherry-oj/judge-engine/sandbox/pool"
	"cherry-oj/judge-engine/sandbox/runner"
	"cherry-oj/judge-engine/sandbox/store"
)

func TestDevhostWaitDelayClosesPool(t *testing.T) {
	disk, err := store.NewDiskStoreWithRoot(filepath.Join(t.TempDir(), "store"))
	if err != nil {
		t.Fatal(err)
	}
	defer disk.Close()
	p, err := pool.New(runner.New(backend.NewDevHost(), disk), pool.Options{Parallelism: 1, QueueSize: 1})
	if err != nil {
		t.Fatal(err)
	}
	defer p.Close()
	marker := filepath.Join(t.TempDir(), "child.pid")
	// The shell exits successfully while its child retains stdout/stderr.
	script := "sleep 30 & echo $! > \"" + marker + "\"; exit 0"
	res, err := p.Run(context.Background(), contract.RunSpec{Command: []string{"sh", "-c", script}})
	data, e := os.ReadFile(marker)
	if e != nil {
		t.Fatal(e)
	}
	pid, e := strconv.Atoi(strings.TrimSpace(string(data)))
	if e != nil {
		t.Fatal(e)
	}
	defer syscall.Kill(pid, syscall.SIGKILL)
	alive := syscall.Kill(pid, 0) == nil
	_, nextErr := p.Run(context.Background(), contract.RunSpec{})
	t.Logf("status=%s error=%q runErr=%v childAlive=%v nextErr=%v", res.Status, res.Error, err, alive, nextErr)
	if res.Status != contract.StatusInternalError || !errors.Is(nextErr, pool.ErrClosed) {
		t.Fatal("pool admits work after WaitDelay left a live child")
	}
}
