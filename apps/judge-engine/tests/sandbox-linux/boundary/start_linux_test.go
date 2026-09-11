package boundary_test

import (
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/cgroup"
	"cherry-oj/judge-engine/internal/sandbox/launcher"
	"golang.org/x/sys/unix"
)

// This suite needs an explicitly owned Linux fixture and delegated jobs subtree.
// An ordinary go test does not provision privileged resources; skip is not support evidence.
func fixture(t *testing.T) (string, string) {
	t.Helper()
	base, jobs := os.Getenv("CHERRY_BOUNDARY_BASE"), os.Getenv("CHERRY_BOUNDARY_JOBS")
	if base == "" || jobs == "" {
		t.Skip("manual Linux fixture/delegation required")
	}
	if filepath.Dir(base) != "/var/lib/cherry-sandbox-test" || !strings.HasPrefix(filepath.Base(base), "work048-boundary-") || !strings.HasPrefix(jobs, "/sys/fs/cgroup/system.slice/cherry-sandbox-test-work048-boundary-") {
		t.Fatal("outside owned fixture")
	}
	return base, jobs
}

func require(t *testing.T, err error) {
	t.Helper()
	if err != nil {
		t.Fatal(err)
	}
}

func TestStartupBoundaries(t *testing.T) {
	base, jobs := fixture(t)
	t.Run("control-wait-deadline", testControlWaitDeadline)
	t.Run("control-signal-observation", observeControlSignal)
	manager, err := cgroup.Open(jobs)
	require(t, err)
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		require(t, manager.Close(ctx))
	}()
	for _, mode := range []string{"configuration-eof", "missing-rootfs", "short-input", "workspace-disconnect", "ready-disconnect", "wrong-go", "liveness-disconnect", "valid-go"} {
		t.Run(mode, func(t *testing.T) {
			group, err := manager.New(cgroup.Limits{MemoryBytes: 64 << 20, MaxProcesses: 64, CPUQuotaNs: 100_000_000, CPUPeriodNs: 100_000_000})
			require(t, err)
			mount, err := os.MkdirTemp(base, "mount-")
			require(t, err)
			var cmd *exec.Cmd
			var waited bool
			defer func() {
				ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
				defer cancel()
				snap, e := group.Stop(ctx)
				require(t, e)
				if snap.Populated {
					t.Error("populated after stop")
				}
				if cmd != nil && cmd.Process != nil && !waited {
					_ = cmd.Wait()
				}
				require(t, group.Close(ctx))
				require(t, os.Remove(mount))
			}()
			control, child, err := launcher.SocketPair()
			require(t, err)
			defer control.Close()
			defer child.Close()
			dataR, dataW, err := os.Pipe()
			require(t, err)
			defer dataR.Close()
			defer dataW.Close()
			lifeR, lifeW, err := os.Pipe()
			require(t, err)
			defer lifeR.Close()
			defer lifeW.Close()
			output, err := os.CreateTemp(base, "output-")
			require(t, err)
			defer os.Remove(output.Name())
			defer output.Close()
			fd, err := group.File()
			require(t, err)
			defer fd.Close()
			cmd = exec.Command(filepath.Join(base, "sandbox-helper"), "--isolated-init")
			cmd.Env = []string{"GOMAXPROCS=1"}
			cmd.Stdout = output
			cmd.Stderr = io.Discard
			cmd.ExtraFiles = []*os.File{child, dataR, lifeR}
			cmd.SysProcAttr = &syscall.SysProcAttr{UseCgroupFD: true, CgroupFD: int(fd.Fd()), Cloneflags: unix.CLONE_NEWNS | unix.CLONE_NEWPID | unix.CLONE_NEWNET | unix.CLONE_NEWIPC | unix.CLONE_NEWUTS | unix.CLONE_NEWCGROUP}
			require(t, cmd.Start())
			require(t, child.Close())
			require(t, dataR.Close())
			require(t, lifeR.Close())
			require(t, fd.Close())
			stage := launcher.StageSpec{Request: launcher.Request{Version: 1, Command: []string{"probe", "identity"}, Limits: contract.Limits{CPUNs: 1_000_000_000, ClockNs: 5_000_000_000, MemoryBytes: 64 << 20, MaxProcesses: 64, StdoutMaxBytes: 8192, StderrMaxBytes: 8192}}, RootFS: filepath.Join(base, "rootfs"), MountPoint: mount, Executable: filepath.Join(base, "sandbox-helper"), PayloadUID: 61002, PayloadGID: 61002, InitUID: 61003, InitGID: 61003, WorkspaceBytes: 8 << 20, WorkspaceInodes: 128}
			if mode == "missing-rootfs" {
				stage.RootFS = filepath.Join(base, "absent-root")
			}
			if mode == "short-input" {
				stage.Request.Inputs = []launcher.Input{{Path: "short", SizeBytes: 1}}
			}
			if mode != "configuration-eof" {
				require(t, launcher.WriteFrame(dataW, stage, launcher.MaxFrameBytes))
			}
			require(t, dataW.Close())
			event := func() launcher.Event {
				t.Helper()
				waiter := eventWaiter{poll: unix.Poll, now: time.Now}
				if err := waiter.wait(control, 2*time.Second); err != nil {
					require(t, fmt.Errorf("startup event poll: %w", err))
				}
				ev, dir, e := launcher.ReceiveEvent(control)
				if e != nil {
					require(t, fmt.Errorf("startup event receive: %w", e))
				}
				if dir != nil {
					require(t, dir.Close())
				}
				t.Logf("event kind=%s phase=%s errno=%d", ev.Kind, ev.Phase, ev.Errno)
				return ev
			}
			wantError := ""
			switch mode {
			case "configuration-eof":
				wantError = "configuration"
			case "missing-rootfs", "short-input":
				wantError = "rootfs-input"
			}
			if wantError != "" {
				ev := event()
				if ev.Kind != "error" || ev.Phase != wantError {
					t.Fatal(ev)
				}
			} else {
				if ev := event(); ev.Kind != "workspace" {
					t.Fatal(ev)
				}
				if mode == "workspace-disconnect" {
					require(t, control.Close())
				} else {
					if ev := event(); ev.Kind != "ready" {
						t.Fatal(ev)
					}
					// No user code may run while the trusted supervisor awaits GO.
					time.Sleep(50 * time.Millisecond)
					info, e := output.Stat()
					require(t, e)
					if info.Size() != 0 {
						t.Fatal("payload ran before GO")
					}
					switch mode {
					case "ready-disconnect":
						require(t, control.Close())
					case "liveness-disconnect":
						require(t, lifeW.Close())
					case "wrong-go":
						_, e = control.Write([]byte{'X'})
						require(t, e)
						ev := event()
						if ev.Kind != "error" || ev.Phase != "go-handshake" {
							t.Fatal(ev)
						}
					case "valid-go":
						_, e = control.Write([]byte{'G'})
						require(t, e)
						ev := event()
						if ev.Kind != "exit" || ev.ExitCode != 0 || ev.ExecFailed {
							t.Fatal(ev)
						}
						require(t, lifeW.Close())
					}
				}
			}
			done := make(chan error, 1)
			go func() { done <- cmd.Wait() }()
			select {
			case <-done:
				waited = true
			case <-time.After(2 * time.Second):
				ctx, cancel := context.WithTimeout(context.Background(), time.Second)
				_, _ = group.Stop(ctx)
				cancel()
				<-done
				waited = true
				t.Fatal("init did not exit after boundary failure")
			}
			info, err := output.Stat()
			require(t, err)
			if mode == "valid-go" {
				if info.Size() == 0 {
					t.Fatal("positive control did not execute")
				}
			} else if info.Size() != 0 {
				t.Fatal("payload ran without valid GO")
			}
		})
	}
	entries, err := os.ReadDir(jobs)
	require(t, err)
	for _, entry := range entries {
		if entry.IsDir() {
			t.Fatalf("remaining cgroup %s", entry.Name())
		}
	}
}
