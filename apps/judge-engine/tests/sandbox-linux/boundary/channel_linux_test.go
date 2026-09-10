package boundary_test

import (
	"context"
	"errors"
	"io"
	"os"
	"os/exec"
	"os/signal"
	"runtime"
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
}

func TestControlSignalChild(t *testing.T) {
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
