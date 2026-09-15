// 白盒测试需要直接验证执行事实映射和错误优先级。
package runner

import (
	"context"
	"errors"
	"io"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/sandbox/internal/backend"
	"cherry-oj/judge-engine/sandbox/internal/store"
)

func TestFactsClassification(t *testing.T) {
	tests := []struct {
		name string
		u    backend.Facts
		ctx  error
		want contract.Status
	}{
		{"sigkill", backend.Facts{Signal: 9}, nil, contract.StatusSignalled},
		{"oom equal limit", backend.Facts{Signal: 9, OOMKilled: true, MemoryBytes: 128 << 20}, nil, contract.StatusMemoryLimitExceeded},
		{"peak without oom", backend.Facts{GroupAccounting: true, MemoryBytes: 129 << 20}, nil, contract.StatusOK},
		{"cpu", backend.Facts{Reason: hostexec.ReasonCPU}, nil, contract.StatusTimeLimitExceeded},
		{"wall", backend.Facts{Reason: hostexec.ReasonWall}, nil, contract.StatusTimeLimitExceeded},
		{"output", backend.Facts{Reason: hostexec.ReasonOutput}, nil, contract.StatusOutputLimitExceeded},
		{"cancel", backend.Facts{}, context.Canceled, contract.StatusInternalError},
		{"caller deadline", backend.Facts{}, context.DeadlineExceeded, contract.StatusInternalError},
		{"unknown", backend.Facts{Reason: "surprise"}, nil, contract.StatusInternalError},
		{"platform despite oom", backend.Facts{Reason: hostexec.ReasonPlatform, OOMKilled: true}, nil, contract.StatusInternalError},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := classify(defaultLimits(), tt.u, false, tt.ctx); got != tt.want {
				t.Fatalf("got %s want %s", got, tt.want)
			}
		})
	}
}

// executeStub 记录是否真的执行过：显式零预算必须在启动之前就得出结论。
type executeStub struct {
	started bool
	err     error
	outputs int
}

func (b *executeStub) Execute(_ context.Context, j backend.Job, sink backend.OutputSink) (backend.Facts, error) {
	b.started = true
	if b.err != nil {
		return backend.Facts{}, b.err
	}
	if sink != nil {
		for _, name := range j.Outputs {
			b.outputs++
			if err := sink(backend.Facts{}, name, strings.NewReader("data")); err != nil {
				return backend.Facts{}, err
			}
		}
	}
	return backend.Facts{}, nil
}

// 显式零预算是执行结论，不是缺省：不能被后端的默认设置重新放宽，也不该启动命令。
func TestExplicitZeroAndExecuteFailure(t *testing.T) {
	st, e := store.NewDiskStoreWithRoot(t.TempDir() + "/store")
	if e != nil {
		t.Fatal(e)
	}
	defer st.Close()
	for _, name := range []string{"cpuNs", "clockNs", "memoryBytes", "maxProcesses"} {
		t.Run(name, func(t *testing.T) {
			l := defaultLimits()
			want := contract.StatusTimeLimitExceeded
			switch name {
			case "cpuNs":
				l.CPUNs = 0
			case "clockNs":
				l.ClockNs = 0
			case "memoryBytes":
				l.MemoryBytes = 0
				want = contract.StatusMemoryLimitExceeded
			case "maxProcesses":
				l.MaxProcesses = 0
				want = contract.StatusInternalError
			}
			b := &executeStub{}
			res, _ := Run(context.Background(), b, st, contract.RunSpec{Command: []string{"true"}, Limits: l})
			if b.started || res.Status != want {
				t.Fatalf("started=%v res=%+v", b.started, res)
			}
		})
	}
	b := &executeStub{err: errors.New("cleanup failed")}
	res, _ := Run(context.Background(), b, st, contract.RunSpec{Command: []string{"true"}, Artifacts: []string{"out"}})
	if res.Status != contract.StatusInternalError || res.Error != "cleanup failed" || len(res.Artifacts) > 0 {
		t.Fatalf("%+v", res)
	}
}

type rollbackStore struct{ puts, deletes int }

func (s *rollbackStore) Put(r io.Reader) (string, error) {
	s.puts++
	if s.puts == 2 {
		return "", errors.New("disk full")
	}
	if _, e := io.Copy(io.Discard, r); e != nil {
		return "", e
	}
	return "saved", nil
}
func (*rollbackStore) Get(string) (io.ReadCloser, error) { return nil, errors.New("unused") }
func (s *rollbackStore) Delete(string) error             { s.deletes++; return nil }

// 全部产物成功才发布 ref：中途失败要把已登记的引用回滚掉，不能留半套。
func TestArtifactFailureRollsBackPublishedRefs(t *testing.T) {
	st := &rollbackStore{}
	res, _ := Run(context.Background(), &executeStub{}, st,
		contract.RunSpec{Command: []string{"true"}, Artifacts: []string{"a", "b"}})
	if res.Status != contract.StatusInternalError || len(res.Artifacts) != 0 || st.deletes != 1 {
		t.Fatalf("res=%+v deleted=%d", res, st.deletes)
	}
}
