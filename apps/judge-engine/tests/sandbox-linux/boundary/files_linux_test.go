package boundary_test

import (
	"errors"
	"io"
	"os"
	"path/filepath"
	"sync"
	"testing"

	"cherry-oj/judge-engine/internal/sandbox/launcher"
	"golang.org/x/sys/unix"
)

func TestOutputPathBoundary(t *testing.T) {
	base, _ := fixture(t)
	dir, err := os.MkdirTemp(base, "files-")
	require(t, err)
	defer os.RemoveAll(dir)
	root, err := os.Open(dir)
	require(t, err)
	defer root.Close()
	write := func(name, body string) {
		t.Helper()
		require(t, os.WriteFile(filepath.Join(dir, name), []byte(body), 0600))
	}
	write("plain", "safe")
	write("secret", "attacker-target")
	require(t, os.Symlink("plain", filepath.Join(dir, "symlink")))
	require(t, os.Symlink("/proc/self/fd/0", filepath.Join(dir, "magic")))
	require(t, os.Link(filepath.Join(dir, "plain"), filepath.Join(dir, "hard")))
	require(t, unix.Mkfifo(filepath.Join(dir, "fifo"), 0600))
	require(t, unix.Mknod(filepath.Join(dir, "device"), unix.S_IFCHR|0600, int(unix.Mkdev(1, 3))))
	require(t, os.Mkdir(filepath.Join(dir, "directory"), 0700))
	for _, name := range []string{"plain", "hard", "symlink", "magic", "fifo", "device", "directory", "../secret", "/etc/passwd"} {
		t.Run(name, func(t *testing.T) {
			f, _, e := launcher.OpenOutput(root, name)
			if f != nil {
				f.Close()
			}
			if e == nil {
				t.Fatal("accepted unsafe output")
			}
			t.Logf("rejected: %v", e)
		})
	}
	require(t, os.Remove(filepath.Join(dir, "hard")))
	// The held FD must not follow a later pathname replacement.
	held, n, err := launcher.OpenOutput(root, "plain")
	require(t, err)
	defer held.Close()
	require(t, os.Rename(filepath.Join(dir, "plain"), filepath.Join(dir, "moved")))
	require(t, os.Symlink("secret", filepath.Join(dir, "plain")))
	data, err := io.ReadAll(held)
	require(t, err)
	if n != 4 || string(data) != "safe" {
		t.Fatal("FD redirected", n, string(data))
	}
	write("race", "safe")
	require(t, os.Symlink("secret", filepath.Join(dir, "alternate")))
	var wg sync.WaitGroup
	failures := make(chan error, 1)
	wg.Add(1)
	go func() {
		defer wg.Done()
		for i := 0; i < 1000; i++ {
			if e := unix.Renameat2(int(root.Fd()), "race", int(root.Fd()), "alternate", unix.RENAME_EXCHANGE); e != nil {
				failures <- e
				return
			}
		}
	}()
	// Join even when an assertion fails, before closing the directory FD.
	defer wg.Wait()
	accepted, rejected := 0, 0
	for i := 0; i < 1000; i++ {
		f, _, e := launcher.OpenOutput(root, "race")
		if e != nil {
			if !errors.Is(e, unix.ELOOP) && !errors.Is(e, unix.EAGAIN) {
				t.Fatal(e)
			}
			rejected++
			continue
		}
		b, e := io.ReadAll(f)
		closeErr := f.Close()
		require(t, errors.Join(e, closeErr))
		if string(b) != "safe" {
			t.Fatal("read attacker target", string(b))
		}
		accepted++
	}
	wg.Wait()
	select {
	case e := <-failures:
		t.Fatal(e)
	default:
	}
	t.Logf("1000 bounded concurrent opens: safe=%d rejected=%d", accepted, rejected)
}
