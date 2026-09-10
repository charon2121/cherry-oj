package boundary_test

import (
	"context"
	"encoding/binary"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
	"testing"
	"time"
	"unsafe"

	"cherry-oj/judge-engine/internal/sandbox/cgroup"
	"cherry-oj/judge-engine/internal/sandbox/launcher"
	"cherry-oj/judge-engine/internal/sandbox/policy"
	"golang.org/x/sys/unix"
)

// Test-only child: a restrictive filter forces real syscall errors without any
// production failure switch. The nonexistent payload can never run on the host.
func TestExecFailureChild(t *testing.T) {
	mode := os.Getenv("CHERRY_BOUNDARY_CHILD_MODE")
	if mode == "" {
		return
	}
	fixture(t)
	cfg := os.NewFile(3, "config")
	var spec launcher.ExecSpec
	require(t, launcher.ReadFrame(cfg, &spec, launcher.MaxFrameBytes))
	require(t, cfg.Close())
	var denied []uint32
	switch mode {
	case "rlimit", "rlimit-einval":
		denied = []uint32{unix.SYS_SETRLIMIT, unix.SYS_PRLIMIT64}
	case "close-range", "close-range-enosys":
		denied = []uint32{unix.SYS_CLOSE_RANGE}
	case "credentials":
		denied = []uint32{unix.SYS_SETGROUPS}
	case "seccomp", "seccomp-enosys":
		denied = []uint32{unix.SYS_SECCOMP}
	}
	injectedErrno := uint32(unix.EPERM)
	if mode == "close-range-enosys" || mode == "seccomp-enosys" {
		injectedErrno = uint32(unix.ENOSYS)
	}
	if mode == "rlimit-einval" {
		injectedErrno = uint32(unix.EINVAL)
	}
	if len(denied) > 0 {
		require(t, unix.Prctl(unix.PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0))
		filter := []unix.SockFilter{{Code: unix.BPF_LD | unix.BPF_W | unix.BPF_ABS, K: 0}}
		for _, number := range denied {
			filter = append(filter, unix.SockFilter{Code: unix.BPF_JMP | unix.BPF_JEQ | unix.BPF_K, K: number, Jf: 1}, unix.SockFilter{Code: unix.BPF_RET | unix.BPF_K, K: unix.SECCOMP_RET_ERRNO | injectedErrno})
		}
		filter = append(filter, unix.SockFilter{Code: unix.BPF_RET | unix.BPF_K, K: unix.SECCOMP_RET_ALLOW})
		program := unix.SockFprog{Len: uint16(len(filter)), Filter: &filter[0]}
		_, _, errno := unix.Syscall(unix.SYS_SECCOMP, unix.SECCOMP_SET_MODE_FILTER, unix.SECCOMP_FILTER_FLAG_TSYNC, uintptr(unsafe.Pointer(&program)))
		if errno != 0 {
			t.Fatal(errno)
		}
	}
	launcher.RunExecStage(spec)
	t.Fatal("exec stage returned")
}

func TestExecStageFailures(t *testing.T) {
	base, jobs := fixture(t)
	manager, err := cgroup.Open(jobs)
	require(t, err)
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		require(t, manager.Close(ctx))
	}()
	for _, tc := range []struct {
		name  string
		stage byte
		errno uint32
	}{
		{"validation", 1, uint32(unix.EINVAL)}, {"rlimit", 2, uint32(unix.EPERM)},
		{"close-range", 3, uint32(unix.EPERM)}, {"credentials", 4, uint32(unix.EPERM)},
		{"seccomp", 5, uint32(unix.EPERM)},
		{"rlimit-einval", 2, uint32(unix.EINVAL)},
		{"close-range-enosys", 3, uint32(unix.ENOSYS)},
		{"seccomp-enosys", 5, uint32(unix.ENOSYS)}, {"exec", 6, uint32(unix.ENOENT)},
		{"wrong-go", 7, uint32(unix.EPIPE)}, {"ready-disconnect", 7, uint32(unix.EPIPE)},
	} {
		t.Run(tc.name, func(t *testing.T) {
			group, e := manager.New(cgroup.Limits{MemoryBytes: 64 << 20, MaxProcesses: 64, CPUQuotaNs: 100_000_000, CPUPeriodNs: 100_000_000})
			require(t, e)
			defer func() {
				ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
				defer cancel()
				snap, e := group.Stop(ctx)
				require(t, e)
				if snap.Populated {
					t.Error("not empty")
				}
				require(t, group.Close(ctx))
			}()
			configR, configW, e := os.Pipe()
			require(t, e)
			defer configR.Close()
			defer configW.Close()
			ready, childReady, e := launcher.SocketPair()
			require(t, e)
			defer ready.Close()
			defer childReady.Close()
			errorR, errorW, e := os.Pipe()
			require(t, e)
			defer errorR.Close()
			defer errorW.Close()
			fd, e := group.File()
			require(t, e)
			defer fd.Close()
			ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
			defer cancel()
			self, e := os.Executable()
			require(t, e)
			cmd := exec.CommandContext(ctx, self, "-test.run=^TestExecFailureChild$")
			cmd.Env = append(os.Environ(), "CHERRY_BOUNDARY_CHILD_MODE="+tc.name, "GOMAXPROCS=1")
			cmd.ExtraFiles = []*os.File{configR, childReady, errorW}
			cmd.SysProcAttr = &syscall.SysProcAttr{UseCgroupFD: true, CgroupFD: int(fd.Fd())}
			require(t, cmd.Start())
			waited := false
			defer func() {
				if !waited {
					_ = cmd.Process.Kill()
					_ = cmd.Wait()
				}
			}()
			require(t, fd.Close())
			require(t, configR.Close())
			require(t, childReady.Close())
			require(t, errorW.Close())
			spec := launcher.ExecSpec{Path: filepath.Join(base, "does-not-exist"), Args: []string{"never-execute"}, UID: 61002, GID: 61002, NoFile: 256, FileSizeBytes: 64 << 20, Profile: policy.Command, ErrorFD: 5}
			if tc.name == "validation" {
				spec.UID = 0
			}
			if tc.stage == 7 {
				spec.ReadyFD = 4
			}
			require(t, launcher.WriteFrame(configW, spec, launcher.MaxFrameBytes))
			require(t, configW.Close())
			if tc.stage == 7 {
				poll := []unix.PollFd{{Fd: int32(ready.Fd()), Events: unix.POLLIN}}
				n, e := unix.Poll(poll, 1000)
				require(t, e)
				if n == 0 {
					t.Fatal("no READY")
				}
				var b [1]byte
				_, e = io.ReadFull(ready, b[:])
				require(t, e)
				if b[0] != 'R' {
					t.Fatal(b)
				}
				if tc.name == "wrong-go" {
					_, e = ready.Write([]byte{'X'})
					require(t, e)
				} else {
					require(t, ready.Close())
				}
			}
			e = cmd.Wait()
			waited = true
			if ctx.Err() != nil || e == nil || cmd.ProcessState.ExitCode() != 125 {
				t.Fatalf("unexpected exit: %v %v", e, ctx.Err())
			}
			var record [8]byte
			_, e = io.ReadFull(errorR, record[:])
			require(t, e)
			if record[0] != tc.stage || binary.LittleEndian.Uint32(record[4:]) != tc.errno {
				t.Fatalf("record=%v want stage=%d errno=%d", record, tc.stage, tc.errno)
			}
			t.Logf("stage=%d errno=%d exit=125", record[0], binary.LittleEndian.Uint32(record[4:]))
		})
	}
}
