package helper

// 白盒检查对象边界和一次移交；内核隔离仍由 Linux 回归证明。
import (
	"context"
	"errors"
	"io"
	"os"
	"reflect"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/internal/sandbox/cgroup"
	"cherry-oj/judge-engine/internal/sandbox/launcher"
)

func TestIsolationPlanOwnsRequestSnapshot(t *testing.T) {
	req := testRequest()
	req.Env = []string{"NAME=original"}
	req.Inputs = []hostexec.Input{{Path: "source", SizeBytes: 1}}
	req.Outputs = []string{"program"}
	original := cloneRequest(req)
	plan := newIsolationPlan(req, Config{RootFS: "/rootfs", PayloadUID: 101, InitUID: 102}, "/helper")
	req.Command[0] = "changed"
	req.Env[0] = "NAME=changed"
	req.Inputs[0].Path = "changed"
	req.Outputs[0] = "changed"
	stage := plan.stage("/mount")
	if !reflect.DeepEqual(stage.Request, original) {
		t.Fatalf("request shared: %+v", stage.Request)
	}
	stage.Request.Command[0] = "changed again"
	stage.Request.Env[0] = "changed"
	stage.Request.Inputs[0].Path = "changed"
	stage.Request.Outputs[0] = "changed"
	if got := plan.stage("/other"); !reflect.DeepEqual(got.Request, original) || got.RootFS != "/rootfs" || got.PayloadUID != 101 || got.InitUID != 102 {
		t.Fatalf("stage shared: %+v", got)
	}
}

type scriptedProcess struct {
	*isolatedProcess
	events   []processEvent
	startErr error
	released bool
}

func (p *scriptedProcess) Start(executionGroup) error { return p.startErr }
func (p *scriptedProcess) Next(context.Context, supervisionTimers) processEvent {
	if len(p.events) == 0 {
		panic("unexpected Next")
	}
	event := p.events[0]
	p.events = p.events[1:]
	return event
}
func (p *scriptedProcess) Release() error { p.released = true; return nil }

func scriptedExecution(events ...processEvent) (*execution, *scriptedProcess, *[]string) {
	var steps []string
	x := newTestExecution(testRequest(), func() { steps = append(steps, "cancel-input") })
	p := &scriptedProcess{isolatedProcess: x.process.(*isolatedProcess), events: events}
	x.process = p
	x.makeGroup = func(cgroup.Limits) (executionGroup, error) {
		steps = append(steps, "create")
		return &lifecycleGroup{steps: &steps}, nil
	}
	return x, p, &steps
}

func TestExecutionRunTransfersArtifactsOnce(t *testing.T) {
	x, p, steps := scriptedExecution(processEvent{kind: processReady}, processEvent{kind: processExited, exitCode: 0})
	x.plan.request.Outputs = []string{"program"}
	dir, err := os.Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	file, err := os.CreateTemp(t.TempDir(), "program")
	if err != nil {
		t.Fatal(err)
	}
	if _, err = file.WriteString("binary"); err != nil {
		t.Fatal(err)
	}
	if _, err = file.Seek(0, io.SeekStart); err != nil {
		t.Fatal(err)
	}
	p.workspace = &testArtifactSource{dir: ownFile(dir), open: func(string) (*os.File, int64, error) { return file, 6, nil }}
	result, fatal := x.Run(context.Background())
	if fatal != nil || !p.released || x.state != executionFinished {
		t.Fatalf("run: %v %+v", fatal, result)
	}
	if !reflect.DeepEqual(*steps, []string{"create", "cancel-input", "stop", "close"}) {
		t.Fatal(*steps)
	}
	if x.result.artifacts != nil || p.TakeWorkspace() != nil {
		t.Fatal("ownership was retained")
	}
	if _, err = dir.Stat(); !errors.Is(err, os.ErrClosed) {
		t.Fatal("workspace leaked", err)
	}
	if err = x.Close(); err != nil {
		t.Fatal(err)
	}
	var out strings.Builder
	if err = result.WriteFiles(&out); err != nil || out.String() != "binary" {
		t.Fatal("execution.Close closed transferred artifacts", err, out.String())
	}
	if _, err = x.Run(context.Background()); err == nil {
		t.Fatal("repeated Run accepted")
	}
	if err = result.Close(); err != nil {
		t.Fatal(err)
	}
	if _, err = file.Stat(); !errors.Is(err, os.ErrClosed) {
		t.Fatal("artifact leaked", err)
	}
}

func TestExecutionRejectsConcurrentRun(t *testing.T) {
	x, p, _ := scriptedExecution(processEvent{kind: processExited})
	entered, release := make(chan struct{}), make(chan struct{})
	original := x.makeGroup
	x.makeGroup = func(l cgroup.Limits) (executionGroup, error) { close(entered); <-release; return original(l) }
	done := make(chan error, 1)
	go func() { result, err := x.Run(context.Background()); done <- errors.Join(err, result.Close()) }()
	<-entered
	if _, err := x.Run(context.Background()); err == nil {
		t.Error("concurrent Run accepted")
	}
	close(release)
	if err := <-done; err != nil {
		t.Fatal(err)
	}
	if p.released {
		t.Fatal("unexpected release")
	}
}

func TestExecutionRechecksBudgetsBeforeRelease(t *testing.T) {
	for _, kind := range []string{"cancel", "wall", "cpu"} {
		t.Run(kind, func(t *testing.T) {
			x, p, _ := scriptedExecution(processEvent{kind: processReady})
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			want := hostexec.ReasonCancelled
			switch kind {
			case "cancel":
				cancel()
			case "wall":
				x.started = time.Now().Add(-time.Duration(x.plan.request.Limits.ClockNs))
				want = hostexec.ReasonWall
			case "cpu":
				x.makeGroup = func(cgroup.Limits) (executionGroup, error) {
					steps := []string{}
					return &lifecycleGroup{steps: &steps, snapshot: cgroup.Snapshot{CPUNs: x.plan.request.Limits.CPUNs}}, nil
				}
				want = hostexec.ReasonCPU
			}
			result, err := x.Run(ctx)
			if err != nil || p.released || result.Reason != want {
				t.Fatalf("released=%v result=%+v error=%v", p.released, result, err)
			}
		})
	}
}

func TestExecutionRunPreservesStartAndCleanupFailure(t *testing.T) {
	x, p, steps := scriptedExecution()
	startErr, closeErr := errors.New("start injected"), errors.New("close injected")
	p.startErr = startErr
	x.makeGroup = func(cgroup.Limits) (executionGroup, error) {
		return &lifecycleGroup{steps: steps, closeErr: closeErr}, nil
	}
	result, err := x.Run(context.Background())
	if !errors.Is(err, closeErr) || !strings.Contains(result.Error, startErr.Error()) || !strings.Contains(result.Error, closeErr.Error()) || x.state != executionCleanupFailed {
		t.Fatalf("%+v %v state=%v", result, err, x.state)
	}
	if err = x.Close(); !errors.Is(err, closeErr) {
		t.Fatal("Close forgot failure", err)
	}
}

func TestProcessReleaseRequiresReadyAndRejectsDuplicate(t *testing.T) {
	p := newIsolatedProcess(isolationPlan{}, strings.NewReader(""), func() {})
	r, w, err := pipe()
	if err != nil {
		t.Fatal(err)
	}
	defer r.Close()
	defer w.Close()
	p.control = w
	p.phase = awaitingReady
	if err = p.Release(); err == nil {
		t.Fatal("GO before ready")
	}
	event := p.acceptEvent(received{event: launcher.Event{Kind: "ready"}})
	if event.kind != processReady {
		t.Fatal(event)
	}
	if err = p.Release(); err != nil {
		t.Fatal(err)
	}
	var b [1]byte
	if _, err = io.ReadFull(r, b[:]); err != nil || b[0] != launcher.PayloadGo {
		t.Fatal(b, err)
	}
	if err = p.Release(); err == nil {
		t.Fatal("duplicate GO")
	}
	if event = p.acceptEvent(received{event: launcher.Event{Kind: "ready"}}); event.kind != processFailure {
		t.Fatal("duplicate ready accepted", event)
	}
}

func TestProcessRejectsUnexpectedFileAndWaitsOnlyOnce(t *testing.T) {
	p := newIsolatedProcess(isolationPlan{}, strings.NewReader(""), func() {})
	file, err := os.Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	event := p.acceptEvent(received{event: launcher.Event{Kind: "ready"}, dir: ownFile(file)})
	if event.kind != processFailure {
		t.Fatal(event)
	}
	if _, err = file.Stat(); !errors.Is(err, os.ErrClosed) {
		t.Fatal("unexpected file leaked", err)
	}
	p.stdoutDone = make(chan captureResult, 1)
	p.stdoutDone <- captureResult{bytes: []byte("output"), exceeded: true}
	first, err := p.Wait(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	second, err := p.Wait(ctx)
	if err != nil || !reflect.DeepEqual(first, second) || !second.stopped || !second.outputExceeded {
		t.Fatalf("second wait changed completion: %+v %v", second, err)
	}
}

func TestArtifactCloseRetainsFirstFailure(t *testing.T) {
	file, err := os.CreateTemp(t.TempDir(), "artifact")
	if err != nil {
		t.Fatal(err)
	}
	if err = file.Close(); err != nil {
		t.Fatal(err)
	}
	set := &artifactSet{files: []*ownedFile{ownFile(file)}}
	first := set.Close()
	second := set.Close()
	if !errors.Is(first, os.ErrClosed) || first != second {
		t.Fatal("Close lost original failure", first, second)
	}
}
