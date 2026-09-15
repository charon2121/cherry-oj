// 使用包内测试以注入伪控制文件和写入/回收故障；不把伪文件测试视为内核行为验证。
package cgroup

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"
	"testing"
	"time"
)

type fakeFilesystem struct {
	mu            sync.Mutex
	files         map[string]string
	dirs          map[string]bool
	writes        []string
	failWrite     string
	killWrites    int
	failRead      string
	failRemove    bool
	holdPopulated bool
	collision     bool
	closeCount    int
}

func newFake() *fakeFilesystem {
	return &fakeFilesystem{files: map[string]string{
		"cgroup.controllers": "cpu memory pids", "cgroup.subtree_control": "cpu memory pids", "cgroup.procs": "", "cgroup.type": "domain",
	}, dirs: map[string]bool{}}
}
func (f *fakeFilesystem) read(n string) ([]byte, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.failRead != "" && strings.HasSuffix(n, f.failRead) {
		return nil, os.ErrPermission
	}
	s, ok := f.files[n]
	if !ok {
		return nil, os.ErrNotExist
	}
	return []byte(s), nil
}
func (f *fakeFilesystem) write(n, v string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.failWrite != "" && strings.HasSuffix(n, f.failWrite) {
		return os.ErrPermission
	}
	if _, ok := f.files[n]; !ok {
		return os.ErrNotExist
	}
	f.writes = append(f.writes, n+"="+v)
	f.files[n] = v
	if strings.HasSuffix(n, "/cgroup.kill") {
		f.killWrites++
	}
	if strings.HasSuffix(n, "/cgroup.kill") && !f.holdPopulated {
		f.files[strings.TrimSuffix(n, "cgroup.kill")+"cgroup.events"] = "populated 0\nfrozen 0"
	}
	return nil
}
func (f *fakeFilesystem) mkdir(n string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.dirs[n] || f.collision {
		return os.ErrExist
	}
	f.dirs[n] = true
	for file, value := range map[string]string{
		"cpu.stat": "usage_usec 0\nuser_usec 0\nsystem_usec 0", "memory.peak": "0", "memory.events": "max 0\noom 0\noom_kill 0", "pids.events": "max 0", "cgroup.events": "populated 0\nfrozen 0", "memory.max": "max", "memory.swap.max": "max", "memory.oom.group": "0", "pids.max": "max", "cpu.max": "max 100000", "cgroup.kill": "",
	} {
		f.files[n+"/"+file] = value
	}
	return nil
}
func (f *fakeFilesystem) remove(n string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.failRemove {
		return os.ErrPermission
	}
	if !f.dirs[n] {
		return os.ErrNotExist
	}
	delete(f.dirs, n)
	for file := range f.files {
		if strings.HasPrefix(file, n+"/") {
			delete(f.files, file)
		}
	}
	return nil
}
func (f *fakeFilesystem) open(string) (*os.File, error) {
	return nil, fmt.Errorf("fake 没有内核 FD")
}
func (f *fakeFilesystem) close() error    { f.mu.Lock(); defer f.mu.Unlock(); f.closeCount++; return nil }
func (f *fakeFilesystem) set(n, v string) { f.mu.Lock(); defer f.mu.Unlock(); f.files[n] = v }

var testLimits = Limits{MemoryBytes: 64 << 20, MaxProcesses: 32, CPUQuotaNs: 10_000_000, CPUPeriodNs: 10_000_000}

func testManager(t *testing.T, f *fakeFilesystem) *Manager {
	t.Helper()
	m, err := newManager(f)
	if err != nil {
		t.Fatal(err)
	}
	return m
}
func cleanupContext(t *testing.T) context.Context {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	t.Cleanup(cancel)
	return ctx
}

func TestFreshGroupsAndFinalAccounting(t *testing.T) {
	f := newFake()
	m := testManager(t, f)
	first, err := m.New(testLimits)
	if err != nil {
		t.Fatal(err)
	}
	f.set(first.name+"/cpu.stat", "usage_usec 17000\nuser_usec 10000\nsystem_usec 7000")
	f.set(first.name+"/memory.peak", "8388608")
	f.set(first.name+"/memory.events", "max 3\noom 1\noom_kill 1")
	f.set(first.name+"/cgroup.events", "populated 1\nfrozen 0")
	s, err := first.Stop(cleanupContext(t))
	if err != nil {
		t.Fatal(err)
	}
	if s.CPUNs != 17_000_000 || s.MemoryBytes != 8<<20 || s.OOMKill != 1 || s.Populated {
		t.Fatalf("snapshot=%+v", s)
	}
	if _, err = first.File(); !errors.Is(err, os.ErrClosed) {
		t.Fatalf("stopped group reused: %v", err)
	}
	if err = first.Close(cleanupContext(t)); err != nil {
		t.Fatal(err)
	}
	second, err := m.New(testLimits)
	if err != nil {
		t.Fatal(err)
	}
	if second.name == first.name {
		t.Fatal("cgroup name reused")
	}
	s, err = second.Snapshot()
	if err != nil {
		t.Fatal(err)
	}
	if s.CPUNs != 0 || s.MemoryBytes != 0 || s.OOMKill != 0 {
		t.Fatalf("history leaked: %+v", s)
	}
	for _, setting := range []string{"memory.swap.max=0", "memory.oom.group=1", "pids.max=32", "cpu.max=10000 10000"} {
		found := false
		for _, write := range f.writes {
			if strings.HasSuffix(write, "/"+setting) {
				found = true
			}
		}
		if !found {
			t.Fatalf("setting absent: %s", setting)
		}
	}
	if err = m.Close(cleanupContext(t)); err != nil {
		t.Fatal(err)
	}
	if err = m.Close(cleanupContext(t)); err != nil {
		t.Fatal(err)
	}
	if len(f.dirs) != 0 || f.closeCount != 1 {
		t.Fatalf("dirs=%v closes=%d", f.dirs, f.closeCount)
	}
}

func TestCleanupTimeoutCanRetry(t *testing.T) {
	f := newFake()
	m := testManager(t, f)
	g, err := m.New(testLimits)
	if err != nil {
		t.Fatal(err)
	}
	f.holdPopulated = true
	f.set(g.name+"/cgroup.events", "populated 1")
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Millisecond)
	defer cancel()
	if err = g.Close(ctx); !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("error=%v", err)
	}
	if !f.dirs[g.name] {
		t.Fatal("populated group removed")
	}
	f.holdPopulated = false
	if err = m.Close(cleanupContext(t)); err != nil {
		t.Fatal(err)
	}
	if len(f.dirs) != 0 {
		t.Fatal("retry did not clean")
	}
}

func TestPartialCreateFailureQuarantinesManager(t *testing.T) {
	f := newFake()
	m := testManager(t, f)
	f.failWrite = "memory.swap.max"
	f.failRemove = true
	if _, err := m.New(testLimits); !errors.Is(err, os.ErrPermission) {
		t.Fatalf("error=%v", err)
	}
	f.failWrite = ""
	if _, err := m.New(testLimits); err == nil {
		t.Fatal("poisoned manager accepted execution")
	}
	if len(f.dirs) != 1 {
		t.Fatalf("unexpected ownership set: %v", f.dirs)
	}
	f.failRemove = false
	if err := m.Close(cleanupContext(t)); err != nil {
		t.Fatal(err)
	}
	if len(f.dirs) != 0 {
		t.Fatal("orphan not recovered")
	}
}

func TestKillAndAccountingFailureNeverBecomeSuccess(t *testing.T) {
	for _, mode := range []string{"kill", "accounting", "events"} {
		t.Run(mode, func(t *testing.T) {
			f := newFake()
			m := testManager(t, f)
			g, err := m.New(testLimits)
			if err != nil {
				t.Fatal(err)
			}
			switch mode {
			case "kill":
				f.failWrite = "cgroup.kill"
			case "accounting":
				f.failRead = "memory.peak"
			case "events":
				f.set(g.name+"/cgroup.events", "frozen 0")
				f.holdPopulated = true
			}
			if _, err = g.Stop(cleanupContext(t)); err == nil {
				t.Fatal("failure hidden")
			}
			if mode != "accounting" && g.empty {
				t.Fatal("unconfirmed group considered empty")
			}
			// 计量失败仍应清理，但调用者必须得到错误；再次 Close 可释放管理器根 FD。
			f.failWrite = ""
			f.failRead = ""
			f.holdPopulated = false
			err = m.Close(cleanupContext(t))
			if mode == "accounting" && err == nil {
				t.Fatal("final accounting failure lost")
			}
			if mode != "accounting" && err != nil {
				t.Fatal(err)
			}
			if err = m.Close(cleanupContext(t)); err != nil {
				t.Fatal(err)
			}
		})
	}
}

func TestRejectWrongHierarchyAndNameCollision(t *testing.T) {
	for _, tc := range []struct{ file, value string }{{"cgroup.controllers", "cpu memory"}, {"cgroup.subtree_control", "memory pids"}, {"cgroup.procs", "123"}, {"cgroup.type", "threaded"}} {
		t.Run(tc.file, func(t *testing.T) {
			f := newFake()
			f.set(tc.file, tc.value)
			if _, err := newManager(f); err == nil {
				t.Fatal("invalid hierarchy accepted")
			}
			if len(f.writes) != 0 {
				t.Fatal("ancestor was modified")
			}
		})
	}
	f := newFake()
	f.collision = true
	m := testManager(t, f)
	if _, err := m.New(testLimits); !errors.Is(err, os.ErrExist) {
		t.Fatalf("collision=%v", err)
	}
	if len(f.writes) != 0 {
		t.Fatal("existing group reused")
	}
	if err := m.Close(cleanupContext(t)); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(t.TempDir()); err == nil {
		t.Fatal("ordinary filesystem accepted")
	}
}

func TestUsageRejectsMalformedAndOverflow(t *testing.T) {
	for _, tc := range []struct{ cpu, peak string }{{"user_usec 1", "0"}, {"usage_usec -1", "0"}, {"usage_usec 9223372036854776", "0"}, {"usage_usec 1\nusage_usec 2", "0"}, {"usage_usec 1", "-1"}, {"usage_usec 1", "9223372036854775808"}, {"usage_usec bad", "0"}, {"", "0"}} {
		if _, err := parseSnapshot([]byte(tc.cpu), []byte(tc.peak), []byte("max 0\noom 0\noom_kill 0"), []byte("max 0"), []byte("populated 0")); err == nil {
			t.Fatalf("invalid usage accepted: %+v", tc)
		}
	}
}

func TestConcurrentCreatesAndBoundedHistory(t *testing.T) {
	f := newFake()
	m := testManager(t, f)
	var wg sync.WaitGroup
	for i := 0; i < 8; i++ {
		wg.Go(func() {
			for j := 0; j < 20; j++ {
				g, err := m.New(testLimits)
				if err != nil {
					t.Error(err)
					return
				}
				ctx, cancel := context.WithTimeout(context.Background(), time.Second)
				err = g.Close(ctx)
				cancel()
				if err != nil {
					t.Error(err)
					return
				}
			}
		})
	}
	wg.Wait()
	if len(m.groups) > 9 {
		t.Fatalf("history retained: %d", len(m.groups))
	}
	if err := m.Close(cleanupContext(t)); err != nil {
		t.Fatal(err)
	}
	if len(f.dirs) != 0 {
		t.Fatal("leaked groups")
	}
}

func TestFailedCloseStopsNewWorkEvenAfterRecovery(t *testing.T) {
	f := newFake()
	m := testManager(t, f)
	g, err := m.New(testLimits)
	if err != nil {
		t.Fatal(err)
	}
	f.failRemove = true
	if err = g.Close(cleanupContext(t)); err == nil {
		t.Fatal("remove failure hidden")
	}
	f.failRemove = false
	if err = g.Close(cleanupContext(t)); err != nil {
		t.Fatal(err)
	}
	if _, err = m.New(testLimits); err == nil {
		t.Fatal("manager silently resumed after failed cleanup")
	}
	if err = m.Close(cleanupContext(t)); err != nil {
		t.Fatal(err)
	}
}

func (f *fakeFilesystem) checkWritable(name string) error {
	_, err := f.read(name)
	return err
}

func TestNewGroupDoesNotIssueKillBeforeFirstProcess(t *testing.T) {
	f := newFake()
	m := testManager(t, f)
	g, err := m.New(testLimits)
	if err != nil {
		t.Fatal(err)
	}
	if f.killWrites != 0 {
		t.Fatal("preflight changed cgroup kill state")
	}
	if _, err = g.Stop(cleanupContext(t)); err != nil {
		t.Fatal(err)
	}
	if f.killWrites != 1 {
		t.Fatal("Stop must still kill the group", f.killWrites)
	}
	if err = m.Close(cleanupContext(t)); err != nil {
		t.Fatal(err)
	}
}
