package pool_test

import (
	"context"
	"errors"
	"io"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/sandbox/internal/backend"
	"cherry-oj/judge-engine/sandbox/internal/pool"
	"cherry-oj/judge-engine/sandbox/internal/runner"
	"cherry-oj/judge-engine/sandbox/internal/store"
	"cherry-oj/judge-engine/sandbox/internal/workspace"
)

type failCleanupStore struct {
	store.Store
	root, ref string
}

func (s *failCleanupStore) Put(r io.Reader) (string, error) {
	ref, err := s.Store.Put(r)
	if err != nil {
		return ref, err
	}
	s.ref = ref
	return ref, os.Chmod(s.root, 0500)
}
func TestIsolatedCleanupFailureRollsBackAndClosesPool(t *testing.T) {
	if os.Geteuid() == 0 {
		t.Skip("permission fault needs non-root")
	}
	dir, err := os.MkdirTemp("/tmp", "s4-wire-")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(dir)
	l, err := net.Listen("unix", filepath.Join(dir, "s"))
	if err != nil {
		t.Fatal(err)
	}
	defer l.Close()
	done := make(chan error, 1)
	go func() {
		c, e := l.Accept()
		if e != nil {
			done <- e
			return
		}
		defer c.Close()
		c.SetDeadline(time.Now().Add(5 * time.Second))
		var req hostexec.Request
		if e = hostexec.ReadFrame(c, &req, hostexec.MaxFrameBytes); e != nil {
			done <- e
			return
		}
		if _, e = io.CopyN(io.Discard, c, req.InputBytes()); e != nil {
			done <- e
			return
		}
		if e = hostexec.WriteFrame(c, hostexec.Result{Version: 1, Outputs: []hostexec.Output{{Path: "out", SizeBytes: 3}}}, 4<<20); e != nil {
			done <- e
			return
		}
		if _, e = io.WriteString(c, "elf"); e != nil {
			done <- e
			return
		}
		done <- hostexec.WriteFrame(c, hostexec.Completion{Version: 1, Complete: true}, 1024)
	}()
	staging := filepath.Join(t.TempDir(), "staging")
	ws, err := workspace.OpenWorkspace(staging)
	if err != nil {
		t.Fatal(err)
	}
	disk, err := store.NewDiskStoreWithRoot(filepath.Join(t.TempDir(), "store"))
	if err != nil {
		t.Fatal(err)
	}
	defer disk.Close()
	st := &failCleanupStore{Store: disk, root: staging}
	b, err := backend.NewIsolated(filepath.Join(dir, "s"), ws)
	if err != nil {
		t.Fatal(err)
	}
	p, err := pool.New(runner.New(b, st), pool.Options{Parallelism: 1, QueueSize: 1})
	if err != nil {
		t.Fatal(err)
	}
	defer func() {
		os.Chmod(staging, 0700)
		p.Close()
		entries, _ := filepath.Glob(filepath.Join(staging, "execution-*"))
		for _, e := range entries {
			os.RemoveAll(e)
		}
		ws.Close()
	}()
	res, err := p.Run(context.Background(), contract.RunSpec{Command: []string{"true"}, Artifacts: []string{"out"}})
	if serverErr := <-done; serverErr != nil {
		t.Fatal(serverErr)
	}
	residue, _ := filepath.Glob(filepath.Join(staging, "execution-*"))
	_, nextErr := p.Run(context.Background(), contract.RunSpec{})
	rc, getErr := st.Get(st.ref)
	if getErr == nil {
		rc.Close()
	}
	t.Logf("status=%s runErr=%v refs=%v residue=%d nextErr=%v artifactStillReadable=%v", res.Status, err, res.Artifacts, len(residue), nextErr, getErr == nil)
	if len(residue) == 0 {
		t.Fatal("fault did not leave a directory")
	}
	if res.Status != contract.StatusInternalError || len(res.Artifacts) != 0 || !errors.Is(nextErr, pool.ErrClosed) || getErr == nil {
		t.Fatal("cleanup failure published artifact or kept pool open")
	}
}
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
