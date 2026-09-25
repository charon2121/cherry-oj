//go:build linux && amd64

package startup_test

import (
	"errors"
	"io"
	"os"
	"testing"
	"time"

	"cherry-oj/judge-engine/isolator/startup"
	"golang.org/x/sys/unix"
)

func TestControlFDIsCloseOnExec(t *testing.T) {
	a, b, err := startup.SocketPair()
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
	if err = startup.SendEvent(a, startup.Event{Kind: "workspace"}, dir); err != nil {
		t.Fatal(err)
	}
	event, fd, err := startup.ReceiveEvent(b)
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
			a, b, err := startup.SocketPair()
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
					done <- startup.SendEvent(a, startup.Event{Kind: "ready"}, nil)
					return
				}
				_, fd, err := startup.ReceiveEvent(a)
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
