package launcher

// White-box tests inject individual syscall outcomes without global hooks.
// Real seqpacket/SCM_RIGHTS and shutdown are covered separately in files_linux_test.go.
import (
	"encoding/json"
	"errors"
	"io"
	"os"
	"testing"

	"golang.org/x/sys/unix"
)

func controlFile(t *testing.T) *os.File {
	t.Helper()
	f, err := os.CreateTemp(t.TempDir(), "control")
	if err != nil {
		t.Fatal(err)
	}
	_ = f.Fd() // Match the blocking files used by SocketPair, including Close semantics.
	t.Cleanup(func() { _ = f.Close() })
	return f
}

func TestControlSendInterruptedOnce(t *testing.T) {
	c, dir := controlFile(t), controlFile(t)
	calls, delivered := 0, 0
	err := sendEvent(c, Event{Kind: "workspace"}, dir, func(fd int, p, oob []byte, _ unix.Sockaddr, _ int) (int, error) {
		calls++
		messages, err := unix.ParseSocketControlMessage(oob)
		if err != nil || len(messages) != 1 {
			t.Fatalf("rights: %v %v", messages, err)
		}
		rights, err := unix.ParseUnixRights(&messages[0])
		if err != nil || len(rights) != 1 || rights[0] != int(dir.Fd()) || fd != int(c.Fd()) {
			t.Fatalf("changed descriptors: %v %v", rights, err)
		}
		if calls <= 3 {
			return -1, unix.EINTR
		}
		var event Event
		if err := json.Unmarshal(p, &event); err != nil || event.Version != Version || event.Kind != "workspace" {
			t.Fatalf("event: %+v %v", event, err)
		}
		delivered++
		return len(p), nil
	})
	if err != nil || calls != 4 || delivered != 1 {
		t.Fatalf("calls=%d delivered=%d err=%v", calls, delivered, err)
	}
}

func TestControlReceiveInterruptedOnce(t *testing.T) {
	c, dir := controlFile(t), controlFile(t)
	calls, delivered := 0, 0
	receive := func(_ int, p, oob []byte, _ int) (int, int, int, unix.Sockaddr, error) {
		calls++
		if calls <= 3 {
			return -1, 0, 0, nil, unix.EINTR
		}
		if calls > 4 {
			return 0, 0, 0, nil, nil
		}
		fd, err := unix.Dup(int(dir.Fd()))
		if err != nil {
			t.Fatal(err)
		}
		delivered++
		n := copy(p, []byte(`{"Version":1,"Kind":"workspace"}`))
		return n, copy(oob, unix.UnixRights(fd)), 0, nil, nil
	}
	event, fd, err := receiveEvent(c, receive)
	if fd != nil {
		defer fd.Close()
	}
	if err != nil || fd == nil || event.Kind != "workspace" || calls != 4 || delivered != 1 {
		t.Fatalf("event=%+v fd=%v calls=%d delivered=%d err=%v", event, fd, calls, delivered, err)
	}
	want, err := dir.Stat()
	if err != nil {
		t.Fatal(err)
	}
	got, err := fd.Stat()
	if err != nil || !os.SameFile(want, got) {
		t.Fatalf("wrong file: %v", err)
	}
	_, extra, err := receiveEvent(c, receive)
	if extra != nil {
		extra.Close()
		t.Fatal("duplicate FD")
	}
	if !errors.Is(err, io.EOF) || calls != 5 || delivered != 1 {
		t.Fatalf("EOF: calls=%d delivered=%d err=%v", calls, delivered, err)
	}
}

func TestControlSendPreservesErrors(t *testing.T) {
	for _, tc := range []struct {
		name string
		n    int
		err  error
	}{
		{"short", 1, nil}, {"partial-eintr", 1, unix.EINTR}, {"eagain", -1, unix.EAGAIN}, {"closed-peer", -1, unix.EPIPE},
	} {
		t.Run(tc.name, func(t *testing.T) {
			c := controlFile(t)
			calls := 0
			err := sendEvent(c, Event{Kind: "ready"}, nil, func(int, []byte, []byte, unix.Sockaddr, int) (int, error) { calls++; return tc.n, tc.err })
			if err == nil || calls != 1 || tc.err != nil && !errors.Is(err, tc.err) {
				t.Fatalf("calls=%d err=%v", calls, err)
			}
		})
	}
}

func TestControlReceiveFailureClosesFDs(t *testing.T) {
	for _, tc := range []struct {
		name, body   string
		flags, count int
		err          error
	}{
		{"json", "!", 0, 1, nil}, {"version", `{"Version":2}`, 0, 1, nil},
		{"truncated", `{"Version":1}`, unix.MSG_TRUNC, 1, nil},
		{"control-truncated", `{"Version":1}`, unix.MSG_CTRUNC, 1, nil},
		{"multiple", `{"Version":1}`, 0, 2, nil},
		{"partial-eintr", `{"Version":1}`, 0, 1, unix.EINTR},
	} {
		t.Run(tc.name, func(t *testing.T) {
			c, dir := controlFile(t), controlFile(t)
			var transferred []int
			defer func() {
				for _, fd := range transferred {
					_ = unix.Close(fd)
				}
			}()
			calls := 0
			_, fd, err := receiveEvent(c, func(_ int, p, oob []byte, _ int) (int, int, int, unix.Sockaddr, error) {
				calls++
				for i := 0; i < tc.count; i++ {
					fd, e := unix.Dup(int(dir.Fd()))
					if e != nil {
						t.Fatal(e)
					}
					transferred = append(transferred, fd)
				}
				return copy(p, tc.body), copy(oob, unix.UnixRights(transferred...)), tc.flags, nil, tc.err
			})
			if fd != nil {
				fd.Close()
				t.Fatal("invalid frame returned FD")
			}
			if err == nil || calls != 1 {
				t.Fatalf("calls=%d err=%v", calls, err)
			}
			for _, fd := range transferred {
				if _, err := unix.FcntlInt(uintptr(fd), unix.F_GETFD, 0); !errors.Is(err, unix.EBADF) {
					t.Fatalf("leaked FD %d: %v", fd, err)
				}
			}
			transferred = nil // Already closed; do not close a possibly reused number later.
		})
	}
}

func TestControlInterruptChecksClosedFiles(t *testing.T) {
	for _, mode := range []string{"receive", "send-control", "send-rights"} {
		t.Run(mode, func(t *testing.T) {
			c, dir := controlFile(t), controlFile(t)
			calls := 0
			stop := c
			if mode == "send-rights" {
				stop = dir
			}
			saved := int(stop.Fd())
			interrupt := func() {
				calls++
				if calls > 32 {
					t.Fatal("syscall retried after file was closed")
				}
				if calls == 32 {
					if err := stop.Close(); err != nil {
						t.Fatal(err)
					}
					// The current syscall still owns its reference; Close must not reuse it.
					if _, err := unix.FcntlInt(uintptr(saved), unix.F_GETFD, 0); err != nil {
						t.Fatalf("unprotected descriptor: %v", err)
					}
				}
			}
			var err error
			if mode == "receive" {
				_, fd, e := receiveEvent(c, func(int, []byte, []byte, int) (int, int, int, unix.Sockaddr, error) {
					interrupt()
					return -1, 0, 0, nil, unix.EINTR
				})
				if fd != nil {
					fd.Close()
					t.Fatal("unexpected FD")
				}
				err = e
			} else {
				err = sendEvent(c, Event{Kind: "workspace"}, dir, func(int, []byte, []byte, unix.Sockaddr, int) (int, error) { interrupt(); return -1, unix.EINTR })
			}
			if err == nil || errors.Is(err, unix.EINTR) || calls != 32 {
				t.Fatalf("calls=%d err=%v", calls, err)
			}
			if _, err := unix.FcntlInt(uintptr(saved), unix.F_GETFD, 0); !errors.Is(err, unix.EBADF) {
				t.Fatalf("held closed FD: %v", err)
			}
		})
	}
}

func TestControlInterruptedSocketDelivery(t *testing.T) {
	a, b, err := SocketPair()
	if err != nil {
		t.Fatal(err)
	}
	defer a.Close()
	defer b.Close()
	dir := controlFile(t)
	sent, received := 0, 0
	err = sendEvent(a, Event{Kind: "workspace"}, dir, func(fd int, p, oob []byte, to unix.Sockaddr, flags int) (int, error) {
		sent++
		if sent <= 3 {
			return -1, unix.EINTR
		}
		return unix.SendmsgN(fd, p, oob, to, flags)
	})
	if err != nil {
		t.Fatal(err)
	}
	if err = a.Close(); err != nil {
		t.Fatal(err)
	}
	event, fd, err := receiveEvent(b, func(fd int, p, oob []byte, flags int) (int, int, int, unix.Sockaddr, error) {
		received++
		if received <= 3 {
			return -1, 0, 0, nil, unix.EINTR
		}
		return unix.Recvmsg(fd, p, oob, flags)
	})
	if fd != nil {
		defer fd.Close()
	}
	if err != nil || fd == nil || event.Kind != "workspace" || sent != 4 || received != 4 {
		t.Fatalf("event=%+v sent=%d received=%d err=%v", event, sent, received, err)
	}
	flags, err := unix.FcntlInt(fd.Fd(), unix.F_GETFD, 0)
	if err != nil || flags&unix.FD_CLOEXEC == 0 {
		t.Fatalf("CLOEXEC: %v", err)
	}
	want, err := dir.Stat()
	if err != nil {
		t.Fatal(err)
	}
	got, err := fd.Stat()
	if err != nil || !os.SameFile(want, got) {
		t.Fatalf("wrong FD: %v", err)
	}
	_, extra, err := ReceiveEvent(b)
	if extra != nil {
		extra.Close()
		t.Fatal("duplicate FD")
	}
	if !errors.Is(err, io.EOF) {
		t.Fatal(err)
	}
}
