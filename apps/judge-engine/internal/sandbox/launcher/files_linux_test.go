package launcher_test

import (
	"bytes"
	"errors"
	"io"
	"os"
	"path/filepath"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/sandbox/launcher"
	"golang.org/x/sys/unix"
)

func TestOutputRejectsLinksAndSpecialFiles(t *testing.T) {
	p := t.TempDir()
	if err := os.WriteFile(filepath.Join(p, "plain"), []byte("value"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("plain", filepath.Join(p, "alias")); err != nil {
		t.Fatal(err)
	}
	if err := os.Link(filepath.Join(p, "plain"), filepath.Join(p, "hard")); err != nil {
		t.Fatal(err)
	}
	if err := unix.Mkfifo(filepath.Join(p, "fifo"), 0600); err != nil {
		t.Fatal(err)
	}
	root, err := os.Open(p)
	if err != nil {
		t.Fatal(err)
	}
	defer root.Close()
	for _, name := range []string{"plain", "hard", "alias", "fifo", "../escape"} {
		f, _, err := launcher.OpenOutput(root, name)
		if err == nil {
			f.Close()
			t.Fatalf("接受 %s", name)
		}
	}
	if err := os.Remove(filepath.Join(p, "hard")); err != nil {
		t.Fatal(err)
	}
	f, n, err := launcher.OpenOutput(root, "plain")
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	if n != 5 {
		t.Fatal(n)
	}
	data, err := io.ReadAll(f)
	if err != nil || !bytes.Equal(data, []byte("value")) {
		t.Fatal(err)
	}
}
func TestControlFDIsCloseOnExec(t *testing.T) {
	a, b, err := launcher.SocketPair()
	if err != nil {
		t.Fatal(err)
	}
	defer a.Close()
	defer b.Close()
	dir, err := os.Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	defer dir.Close()
	if err = launcher.SendEvent(a, launcher.Event{Kind: "workspace"}, dir); err != nil {
		t.Fatal(err)
	}
	event, fd, err := launcher.ReceiveEvent(b)
	if err != nil {
		t.Fatal(err)
	}
	if fd == nil {
		t.Fatal("missing fd")
	}
	defer fd.Close()
	if event.Kind != "workspace" {
		t.Fatal(event)
	}
	flags, err := unix.FcntlInt(fd.Fd(), unix.F_GETFD, 0)
	if err != nil || flags&unix.FD_CLOEXEC == 0 {
		t.Fatal("received FD inherits across exec", err)
	}
}

// The helper cancels blocked control I/O using shutdown before closing os.File.
// Pinning a descriptor during recvmsg/sendmsg must not break this wakeup path.
func TestControlShutdownUnblocksIO(t *testing.T) {
	for _, mode := range []string{"receive", "send"} {
		t.Run(mode, func(t *testing.T) {
			a, b, err := launcher.SocketPair()
			if err != nil {
				t.Fatal(err)
			}
			defer a.Close()
			defer b.Close()
			defer unix.Shutdown(int(a.Fd()), unix.SHUT_RDWR)
			if mode == "send" {
				if err := unix.SetsockoptInt(int(a.Fd()), unix.SOL_SOCKET, unix.SO_SNDBUF, 4096); err != nil {
					t.Fatal(err)
				}
				full := false
				for i := 0; i < 4096; i++ {
					_, err := unix.SendmsgN(int(a.Fd()), make([]byte, 1024), nil, nil, unix.MSG_DONTWAIT)
					if errors.Is(err, unix.EAGAIN) {
						full = true
						break
					}
					if err != nil {
						t.Fatal(err)
					}
				}
				if !full {
					t.Fatal("send queue did not fill within fixture bound")
				}
			}
			done := make(chan error, 1)
			go func() {
				if mode == "send" {
					done <- launcher.SendEvent(a, launcher.Event{Kind: "ready"}, nil)
					return
				}
				_, fd, err := launcher.ReceiveEvent(a)
				if fd != nil {
					fd.Close()
					err = errors.New("unexpected FD")
				}
				done <- err
			}()
			select {
			case err := <-done:
				t.Fatalf("I/O returned before shutdown: %v", err)
			case <-time.After(20 * time.Millisecond):
			}
			start := time.Now()
			if err := unix.Shutdown(int(a.Fd()), unix.SHUT_RDWR); err != nil {
				t.Fatal(err)
			}
			select {
			case err := <-done:
				if mode == "receive" && !errors.Is(err, io.EOF) {
					t.Fatalf("receive: %v", err)
				}
				if mode == "send" && err == nil {
					t.Fatal("send succeeded after shutdown")
				}
				if time.Since(start) > time.Second {
					t.Fatal("shutdown exceeded bound")
				}
			case <-time.After(time.Second):
				t.Fatal("control I/O survived shutdown")
			}
		})
	}
}
