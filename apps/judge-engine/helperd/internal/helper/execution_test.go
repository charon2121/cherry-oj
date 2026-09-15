package helper

// 白盒测试生命周期顺序和句柄所有权；不创建 namespace/cgroup，不证明 Linux 内核行为。
import (
	"context"
	"errors"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"strings"
	"sync"
	"testing"

	"cherry-oj/judge-engine/helperd/internal/cgroup"
	"cherry-oj/judge-engine/internal/hostexec"
)

type lifecycleGroup struct {
	steps             *[]string
	stopErr, closeErr error
	snapshot          cgroup.Snapshot
}

func (g *lifecycleGroup) File() (*os.File, error)            { return nil, errors.New("not used") }
func (g *lifecycleGroup) Snapshot() (cgroup.Snapshot, error) { return g.snapshot, nil }
func (g *lifecycleGroup) Stop(ctx context.Context) (cgroup.Snapshot, error) {
	*g.steps = append(*g.steps, "stop")
	return g.snapshot, errors.Join(g.stopErr, ctx.Err())
}
func (g *lifecycleGroup) Close(ctx context.Context) error {
	*g.steps = append(*g.steps, "close")
	return errors.Join(g.closeErr, ctx.Err())
}

func TestFinishOrdersCleanupAndWithholdsUnsafeArtifacts(t *testing.T) {
	for _, failure := range []string{"none", "stop", "wait", "close", "output"} {
		t.Run(failure, func(t *testing.T) {
			var steps []string
			injected := errors.New("injected " + failure)
			req := testRequest()
			req.Outputs = []string{"program"}
			x := newTestExecution(req, func() { steps = append(steps, "cancel-input") })
			group := &lifecycleGroup{steps: &steps, snapshot: cgroup.Snapshot{CPUNs: 1}}
			if failure == "stop" {
				group.stopErr = injected
			}
			if failure == "close" {
				group.closeErr = injected
			}
			x.group = group
			dir, err := os.Open(t.TempDir())
			if err != nil {
				t.Fatal(err)
			}
			x.process.(*isolatedProcess).workspace = &testArtifactSource{dir: ownFile(dir)}
			x.process.(*isolatedProcess).initStopped = false
			x.process.(*isolatedProcess).initExited = make(chan error, 1)
			if failure == "wait" {
				x.process.(*isolatedProcess).initExited <- injected
			} else {
				x.process.(*isolatedProcess).initExited <- nil
			}
			x.process.(*isolatedProcess).events = make(chan received)
			close(x.process.(*isolatedProcess).events)
			x.process.(*isolatedProcess).workspace.(*testArtifactSource).open = func(_ string) (*os.File, int64, error) {
				steps = append(steps, "open-output")
				if failure == "output" {
					return nil, 0, injected
				}
				path := filepath.Join(t.TempDir(), "artifact")
				if err := os.WriteFile(path, []byte("binary"), 0600); err != nil {
					t.Fatal(err)
				}
				f, err := os.Open(path)
				return f, 6, err
			}
			ctx, cancel := context.WithCancel(context.Background())
			cancel()
			result, fatal := x.finish(ctx)
			if !result.Cancelled {
				t.Fatal("lost cancellation")
			}
			want := []string{"cancel-input", "stop"}
			if failure != "stop" && failure != "wait" {
				want = append(want, "open-output")
			}
			want = append(want, "close")
			if !reflect.DeepEqual(steps, want) {
				t.Fatalf("steps=%v want=%v", steps, want)
			}
			if _, err := dir.Stat(); !errors.Is(err, os.ErrClosed) {
				t.Fatal("workspace not closed", err)
			}
			if failure == "none" {
				if fatal != nil {
					t.Fatal(fatal)
				}
				var output strings.Builder
				if err := result.WriteFiles(&output); err != nil {
					t.Fatal(err)
				}
				if output.String() != "binary" {
					t.Fatal(output.String())
				}
			} else {
				if result.Reason != hostexec.ReasonPlatform || !strings.Contains(result.Error, injected.Error()) {
					t.Fatalf("lost failure: %+v", result)
				}
				if result.artifacts != nil && len(result.artifacts.files) != 0 || len(result.Outputs) != 0 {
					t.Fatal("published unsafe artifacts")
				}
				if failure != "output" && !errors.Is(fatal, injected) {
					t.Fatal("cleanup failure must stop node", fatal)
				}
			}
			if err := result.Close(); err != nil {
				t.Fatal(err)
			}
		})
	}
}

func TestFinishPreservesExecutionAndMultipleCleanupErrors(t *testing.T) {
	var steps []string
	runErr := errors.New("start failed")
	stopErr := errors.New("stop failed")
	closeErr := errors.New("close failed")
	x := newTestExecution(testRequest(), func() {})
	x.group = &lifecycleGroup{steps: &steps, stopErr: stopErr, closeErr: closeErr}
	x.fail(runErr)
	result, fatal := x.finish(context.Background())
	for _, err := range []error{stopErr, closeErr} {
		if !errors.Is(fatal, err) {
			t.Fatal("lost cleanup cause", fatal)
		}
	}
	for _, err := range []error{runErr, stopErr, closeErr} {
		if !strings.Contains(result.Error, err.Error()) {
			t.Fatal("overwritten error", result.Error)
		}
	}
}

func TestOwnedFileConcurrentCloseUnblocksReader(t *testing.T) {
	r, w, err := pipe()
	if err != nil {
		t.Fatal(err)
	}
	readDone := make(chan error, 1)
	go func() { var b [1]byte; _, err := r.Read(b[:]); readDone <- err }()
	var wg sync.WaitGroup
	for range 16 {
		wg.Go(func() {
			if err := r.Close(); err != nil {
				t.Error(err)
			}
		})
	}
	wg.Wait()
	if err := <-readDone; !errors.Is(err, os.ErrClosed) {
		t.Fatal(err)
	}
	if err := w.Close(); err != nil {
		t.Fatal(err)
	}
}

func TestFinishClosesUnconsumedReceivedFiles(t *testing.T) {
	var steps []string
	x := newTestExecution(testRequest(), func() {})
	x.group = &lifecycleGroup{steps: &steps}
	f, err := os.Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	x.process.(*isolatedProcess).events = make(chan received, 1)
	x.process.(*isolatedProcess).events <- received{dir: ownFile(f)}
	close(x.process.(*isolatedProcess).events)
	_, err = x.finish(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if _, err = f.Stat(); !errors.Is(err, os.ErrClosed) {
		t.Fatal("SCM_RIGHTS leaked", err)
	}
}

func TestDeliveryRejectsTruncatedArtifact(t *testing.T) {
	f, err := os.CreateTemp(t.TempDir(), "artifact")
	if err != nil {
		t.Fatal(err)
	}
	r := executionResult{Result: hostexec.Result{Outputs: []hostexec.Output{{Path: "artifact", SizeBytes: 1}}}, artifacts: &artifactSet{files: []*ownedFile{ownFile(f)}}}
	if err := r.WriteFiles(io.Discard); !errors.Is(err, io.EOF) {
		t.Fatal(err)
	}
	if err := r.Close(); err != nil {
		t.Fatal(err)
	}
}

func TestFinishCancelsBlockedInputBeforeWaiting(t *testing.T) {
	r, w, err := pipe()
	if err != nil {
		t.Fatal(err)
	}
	var steps []string
	x := newTestExecution(testRequest(), func() {
		if err := r.Close(); err != nil {
			t.Error(err)
		}
	})
	x.group = &lifecycleGroup{steps: &steps}
	x.process.(*isolatedProcess).inputFinished = make(chan struct{})
	go func() { defer close(x.process.(*isolatedProcess).inputFinished); var b [1]byte; _, _ = r.Read(b[:]) }()
	_, err = x.finish(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	select {
	case <-x.process.(*isolatedProcess).inputFinished:
	default:
		t.Fatal("returned before input goroutine finished")
	}
	if err := w.Close(); err != nil {
		t.Fatal(err)
	}
}

func TestFinishClosesEarlierArtifactsWhenLaterOpenFails(t *testing.T) {
	var steps []string
	req := testRequest()
	req.Outputs = []string{"first", "second"}
	x := newTestExecution(req, func() {})
	x.group = &lifecycleGroup{steps: &steps}
	dir, err := os.Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	x.process.(*isolatedProcess).workspace = &testArtifactSource{dir: ownFile(dir)}
	file, err := os.CreateTemp(t.TempDir(), "artifact")
	if err != nil {
		t.Fatal(err)
	}
	denied := errors.New("unsafe second artifact")
	x.process.(*isolatedProcess).workspace.(*testArtifactSource).open = func(name string) (*os.File, int64, error) {
		if name == "first" {
			return file, 0, nil
		}
		return nil, 0, denied
	}
	result, fatal := x.finish(context.Background())
	if fatal != nil {
		t.Fatal(fatal)
	}
	if result.Reason != hostexec.ReasonPlatform || len(result.Outputs) != 0 || result.artifacts != nil && len(result.artifacts.files) != 0 {
		t.Fatal(result)
	}
	if _, err := file.Stat(); !errors.Is(err, os.ErrClosed) {
		t.Fatal("partial artifact handle leaked", err)
	}
}

func TestFinishOOMDoesNotHideIndependentFailure(t *testing.T) {
	var steps []string
	x := newTestExecution(testRequest(), func() {})
	x.group = &lifecycleGroup{steps: &steps, snapshot: cgroup.Snapshot{OOM: 1, OOMKill: 1}}
	failure := errors.New("snapshot failed")
	x.fail(failure)
	result, fatal := x.finish(context.Background())
	if fatal != nil {
		t.Fatal(fatal)
	}
	if result.Reason != hostexec.ReasonPlatform || !strings.Contains(result.Error, failure.Error()) {
		t.Fatal(result)
	}
}

func TestFinalOOMSurvivesInitExitDiagnostic(t *testing.T) {
	var steps []string
	x := newTestExecution(testRequest(), func() {})
	x.group = &lifecycleGroup{steps: &steps, snapshot: cgroup.Snapshot{OOM: 1, OOMKill: 1}}
	x.initReportLost = true
	x.process.(*isolatedProcess).initStopped = false
	x.process.(*isolatedProcess).initExited = make(chan error, 1)
	err := exec.Command("sh", "-c", "exit 1").Run()
	var exit *exec.ExitError
	if !errors.As(err, &exit) {
		t.Fatal(err)
	}
	x.process.(*isolatedProcess).initExited <- err
	x.fail(io.EOF)
	result, fatal := x.finish(context.Background())
	if fatal != nil || result.Reason != "" || result.Error != "" || result.Signal != 9 {
		t.Fatalf("OOM overwritten: %+v %v", result, fatal)
	}
}

func TestAncestorOOMPreservesPlatformFailure(t *testing.T) {
	for _, initReportLost := range []bool{false, true} {
		var steps []string
		x := newTestExecution(testRequest(), func() {})
		x.group = &lifecycleGroup{steps: &steps, snapshot: cgroup.Snapshot{OOMKill: 2}}
		x.initReportLost = initReportLost
		if initReportLost {
			x.fail(io.EOF)
		}
		result, fatal := x.finish(context.Background())
		if fatal != nil || result.Reason != hostexec.ReasonPlatform || !strings.Contains(result.Error, "without task-local OOM") || len(result.Outputs) != 0 {
			t.Fatalf("ancestor OOM misattributed: %+v fatal=%v", result, fatal)
		}
	}
}

func newTestExecution(r hostexec.Request, cancel context.CancelFunc) *execution {
	return newExecution(r, executionOptions{source: strings.NewReader(""), cancelInput: cancel})
}

type testArtifactSource struct {
	dir  *ownedFile
	open func(string) (*os.File, int64, error)
}

func (s *testArtifactSource) Open(name string) (*os.File, int64, error) { return s.open(name) }
func (s *testArtifactSource) Close() error                              { return s.dir.Close() }
