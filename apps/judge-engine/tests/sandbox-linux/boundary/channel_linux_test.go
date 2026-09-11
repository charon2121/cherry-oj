package boundary_test

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"os/signal"
	"reflect"
	"runtime"
	"strconv"
	"strings"
	"testing"
	"time"
	"unsafe"

	"cherry-oj/judge-engine/internal/sandbox/launcher"
	"golang.org/x/sys/unix"
)

// Keep the signal mask and handler experiment in its own bounded child. This
// diagnoses the pre-receive wait; it does not retry or suppress a startup error.
func observeControlSignal(t *testing.T) {
	t.Helper()
	executable, err := os.Executable()
	require(t, err)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, executable, "-test.run=^TestControlSignalChild$", "-test.v")
	cmd.Env = append(os.Environ(), "CHERRY_BOUNDARY_SIGNAL_CHILD=1")
	cmd.WaitDelay = time.Second
	output, err := cmd.CombinedOutput()
	t.Logf("%s", output)
	require(t, err)
	if !strings.Contains(string(output), "poll=EINTR receiveCallsBeforeSend=0 message=workspace fd=once eof=true") {
		t.Fatal("missing control signal observation")
	}

	cmd = exec.CommandContext(ctx, executable, "-test.run=^TestControlSignalChild$", "-test.v")
	cmd.Env = append(os.Environ(), "CHERRY_BOUNDARY_SIGNAL_CHILD=receive")
	cmd.WaitDelay = time.Second
	output, err = cmd.CombinedOutput()
	t.Logf("%s", output)
	require(t, err)
	if !strings.Contains(string(output), "recvmsg-blocked=true signal=SIGUSR1 message=workspace fd=once eof=true") {
		t.Fatal("missing receive signal observation")
	}
}

func TestControlSignalChild(t *testing.T) {
	if os.Getenv("CHERRY_BOUNDARY_SIGNAL_CHILD") == "receive" {
		fixture(t)
		observeReceiveSignal(t)
		return
	}
	if os.Getenv("CHERRY_BOUNDARY_SIGNAL_CHILD") != "1" {
		return
	}
	fixture(t)
	control, peer, err := launcher.SocketPair()
	require(t, err)
	defer control.Close()
	defer peer.Close()
	dir, err := os.Open(t.TempDir())
	require(t, err)
	defer dir.Close()

	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	notified := make(chan os.Signal, 1)
	signal.Notify(notified, unix.SIGUSR1)
	defer signal.Stop(notified)
	var blocked, previous unix.Sigset_t
	blocked.Val[0] = 1 << (uint(unix.SIGUSR1) - 1)
	require(t, unix.PthreadSigmask(unix.SIG_BLOCK, &blocked, &previous))
	defer func() { require(t, unix.PthreadSigmask(unix.SIG_SETMASK, &previous, nil)) }()
	require(t, unix.Tgkill(os.Getpid(), unix.Gettid(), unix.SIGUSR1))

	// x/sys Poll delegates to Ppoll. Atomically unmask a pending thread signal
	// during this empty-socket wait, so EINTR does not depend on sleep timing.
	unblocked := previous
	unblocked.Val[0] &^= blocked.Val[0]
	fds := []unix.PollFd{{Fd: int32(control.Fd()), Events: unix.POLLIN}}
	timeout := unix.NsecToTimespec((2 * time.Second).Nanoseconds())
	// x/sys v0.46.0 Ppoll passes a zero sigsetsize, valid only for a nil mask.
	// This linux/amd64 fixture supplies the kernel's 64-bit mask explicitly;
	// libc's 128-byte Sigset_t is not the kernel ABI size.
	kernelMask := uint64(unblocked.Val[0])
	_, _, errno := unix.Syscall6(unix.SYS_PPOLL, uintptr(unsafe.Pointer(&fds[0])), uintptr(len(fds)),
		uintptr(unsafe.Pointer(&timeout)), uintptr(unsafe.Pointer(&kernelMask)), unsafe.Sizeof(kernelMask), 0)
	if errno != unix.EINTR {
		t.Fatalf("pending signal poll: %v", errno)
	}
	select {
	case <-notified:
	case <-time.After(time.Second):
		t.Fatal("signal handler did not observe SIGUSR1")
	}

	// The production receiver has not run yet. Deliver one event plus one FD
	// afterwards and require EOF, checking that this signal did not consume it.
	require(t, launcher.SendEvent(peer, launcher.Event{Kind: "workspace"}, dir))
	require(t, peer.Close())
	event, received, err := launcher.ReceiveEvent(control)
	require(t, err)
	if received == nil {
		t.Fatal("missing workspace FD")
	}
	defer received.Close()
	want, err := dir.Stat()
	require(t, err)
	got, err := received.Stat()
	require(t, err)
	if event.Kind != "workspace" || !os.SameFile(want, got) {
		t.Fatal("unexpected event or workspace FD")
	}
	_, extra, err := launcher.ReceiveEvent(control)
	if extra != nil {
		require(t, extra.Close())
		t.Fatal("duplicate workspace FD")
	}
	if !errors.Is(err, io.EOF) {
		t.Fatalf("expected EOF after one event: %v", err)
	}
	t.Log("poll=EINTR receiveCallsBeforeSend=0 message=workspace fd=once eof=true")
}

// A socket timeout disables signal restart for this test socket. Observe the
// receiving thread inside recvmsg before sending its signal, without adding a
// signal handler or fault injection switch to production code.
func observeReceiveSignal(t *testing.T) {
	control, peer, err := launcher.SocketPair()
	require(t, err)
	defer control.Close()
	defer peer.Close()
	defer unix.Shutdown(int(control.Fd()), unix.SHUT_RDWR)
	timeout := unix.NsecToTimeval((2 * time.Second).Nanoseconds())
	require(t, unix.SetsockoptTimeval(int(control.Fd()), unix.SOL_SOCKET, unix.SO_RCVTIMEO, &timeout))
	notified := make(chan os.Signal, 1)
	signal.Notify(notified, unix.SIGUSR1)
	defer signal.Stop(notified)
	type response struct {
		event launcher.Event
		file  *os.File
		err   error
	}
	done := make(chan response, 1)
	thread := make(chan int, 1)
	go func() {
		runtime.LockOSThread()
		defer runtime.UnlockOSThread()
		thread <- unix.Gettid()
		event, file, err := launcher.ReceiveEvent(control)
		done <- response{event, file, err}
	}()
	tid := <-thread
	deadline := time.Now().Add(time.Second)
	blocked := false
	for time.Now().Before(deadline) {
		state, e := os.ReadFile(fmt.Sprintf("/proc/self/task/%d/syscall", tid))
		require(t, e)
		fields := strings.Fields(string(state))
		if len(fields) > 0 && fields[0] == strconv.Itoa(unix.SYS_RECVMSG) {
			blocked = true
			break
		}
		time.Sleep(time.Millisecond)
	}
	if !blocked {
		t.Fatal("receiver never entered recvmsg")
	}
	require(t, unix.Tgkill(os.Getpid(), tid, unix.SIGUSR1))
	select {
	case <-notified:
	case <-time.After(time.Second):
		t.Fatal("SIGUSR1 was not observed")
	}
	select {
	case r := <-done:
		if r.file != nil {
			r.file.Close()
		}
		t.Fatalf("receive returned on signal before message: %v", r.err)
	case <-time.After(20 * time.Millisecond):
	}
	dir, e := os.Open(t.TempDir())
	require(t, e)
	defer dir.Close()
	require(t, launcher.SendEvent(peer, launcher.Event{Kind: "workspace"}, dir))
	require(t, peer.Close())
	select {
	case r := <-done:
		if r.file != nil {
			defer r.file.Close()
		}
		require(t, r.err)
		if r.file == nil || r.event.Kind != "workspace" {
			t.Fatal("wrong event/FD")
		}
		want, e := dir.Stat()
		require(t, e)
		got, e := r.file.Stat()
		require(t, e)
		if !os.SameFile(want, got) {
			t.Fatal("wrong workspace FD")
		}
	case <-time.After(time.Second):
		t.Fatal("message not delivered after signal")
	}
	_, extra, e := launcher.ReceiveEvent(control)
	if extra != nil {
		extra.Close()
		t.Fatal("duplicate FD")
	}
	if !errors.Is(e, io.EOF) {
		t.Fatalf("expected EOF: %v", e)
	}
	t.Log("recvmsg-blocked=true signal=SIGUSR1 message=workspace fd=once eof=true")
}

// Only the test's readiness syscall is retried. The caller receives one event
// afterwards, and the original deadline is never restarted by a signal.
type eventWaiter struct {
	poll func([]unix.PollFd, int) (int, error)
	now  func() time.Time
}

func (w eventWaiter) wait(control *os.File, budget time.Duration) error {
	deadline := w.now().Add(budget)
	raw, err := control.SyscallConn()
	if err != nil {
		return err
	}
	for {
		remaining := deadline.Sub(w.now())
		if remaining <= 0 {
			return os.ErrDeadlineExceeded
		}
		milliseconds := int((remaining + time.Millisecond - 1) / time.Millisecond)
		var n int
		var pollErr error
		var events int16
		if err := raw.Control(func(fd uintptr) {
			fds := []unix.PollFd{{Fd: int32(fd), Events: unix.POLLIN}}
			n, pollErr = w.poll(fds, milliseconds)
			events = fds[0].Revents
		}); err != nil {
			return err
		}
		if pollErr == unix.EINTR {
			continue
		}
		if pollErr != nil {
			return pollErr
		}
		if events&unix.POLLNVAL != 0 {
			return unix.EBADF
		}
		if n == 0 {
			return os.ErrDeadlineExceeded
		}
		return nil
	}
}

func testControlWaitDeadline(t *testing.T) {
	for _, mode := range []string{"continuous-interruption", "ready-after-interruption", "io-error", "invalid-fd"} {
		t.Run(mode, func(t *testing.T) {
			c, err := os.CreateTemp(t.TempDir(), "control")
			require(t, err)
			defer c.Close()
			now := time.Unix(0, 0)
			var waits []int
			waiter := eventWaiter{now: func() time.Time { return now }}
			waiter.poll = func(fds []unix.PollFd, ms int) (int, error) {
				waits = append(waits, ms)
				if len(waits) > 3 {
					t.Fatal("deadline restarted on interruption")
				}
				switch mode {
				case "continuous-interruption":
					step := 700 * time.Millisecond
					if len(waits) == 3 {
						step = 600 * time.Millisecond
					}
					now = now.Add(step)
					return -1, unix.EINTR
				case "ready-after-interruption":
					if len(waits) == 1 {
						now = now.Add(200 * time.Millisecond)
						return -1, unix.EINTR
					}
					fds[0].Revents = unix.POLLIN
					return 1, nil
				case "io-error":
					return -1, unix.EIO
				default:
					fds[0].Revents = unix.POLLNVAL
					return 1, nil
				}
			}
			err = waiter.wait(c, 2*time.Second)
			switch mode {
			case "continuous-interruption":
				if !errors.Is(err, os.ErrDeadlineExceeded) || !reflect.DeepEqual(waits, []int{2000, 1300, 600}) {
					t.Fatalf("waits=%v err=%v", waits, err)
				}
			case "ready-after-interruption":
				if err != nil || !reflect.DeepEqual(waits, []int{2000, 1800}) {
					t.Fatalf("waits=%v err=%v", waits, err)
				}
			default:
				want := unix.EIO
				if mode == "invalid-fd" {
					want = unix.EBADF
				}
				if !errors.Is(err, want) || len(waits) != 1 {
					t.Fatalf("waits=%v err=%v", waits, err)
				}
			}
		})
	}
}
