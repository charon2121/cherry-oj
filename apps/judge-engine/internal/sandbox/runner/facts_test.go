// 白盒测试需要直接验证执行事实映射和错误优先级。
package runner

import (
	"context"
	"errors"
	"io"
	"io/fs"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/container"
	"cherry-oj/judge-engine/internal/sandbox/store"
)

func TestFactsClassification(t *testing.T) {
	tests := []struct {
		name string
		u    container.Usage
		ctx  error
		want contract.Status
	}{
		{"sigkill", container.Usage{Signal: 9}, nil, contract.StatusSignalled},
		{"oom equal limit", container.Usage{Signal: 9, OOMKilled: true, MemoryBytes: 128 << 20}, nil, contract.StatusMemoryLimitExceeded},
		{"peak without oom", container.Usage{GroupAccounting: true, MemoryBytes: 129 << 20}, nil, contract.StatusOK},
		{"cpu", container.Usage{Reason: container.ReasonCPU}, nil, contract.StatusTimeLimitExceeded},
		{"wall", container.Usage{Reason: container.ReasonWall}, nil, contract.StatusTimeLimitExceeded},
		{"output", container.Usage{Reason: container.ReasonOutput}, nil, contract.StatusOutputLimitExceeded},
		{"cancel", container.Usage{}, context.Canceled, contract.StatusInternalError},
		{"caller deadline", container.Usage{}, context.DeadlineExceeded, contract.StatusInternalError},
		{"unknown", container.Usage{Reason: "surprise"}, nil, contract.StatusInternalError},
		{"platform despite oom", container.Usage{Reason: container.ReasonPlatform, OOMKilled: true}, nil, contract.StatusInternalError},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := classify(defaultLimits(), tt.u, false, tt.ctx); got != tt.want {
				t.Fatalf("got %s want %s", got, tt.want)
			}
		})
	}
}

type failedWait struct{}

func (failedWait) Wait(context.Context) (container.Usage, error) {
	return container.Usage{}, errors.New("cleanup failed")
}

type executionStub struct {
	started   bool
	waitError bool
}

func (c *executionStub) Start(context.Context, container.Spec) (container.Process, error) {
	c.started = true
	return failedWait{}, nil
}
func (*executionStub) PutFile(string, io.Reader, fs.FileMode) error { return nil }
func (*executionStub) GetFile(string) (io.ReadCloser, error) {
	return io.NopCloser(strings.NewReader("data")), nil
}
func (*executionStub) Close() error { return nil }
func TestExplicitZeroAndWaitFailure(t *testing.T) {
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
			c := &executionStub{}
			res := Run(context.Background(), c, st, contract.RunSpec{Command: []string{"true"}, Limits: l})
			if c.started || res.Status != want {
				t.Fatalf("started=%v res=%+v", c.started, res)
			}
		})
	}
	res := Run(context.Background(), &executionStub{}, st, contract.RunSpec{Command: []string{"true"}, Artifacts: []string{"out"}})
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
func TestArtifactFailureRollsBackPublishedRefs(t *testing.T) {
	st := &rollbackStore{}
	res := collect(&executionStub{}, st, contract.RunSpec{Artifacts: []string{"a", "b"}}, contract.RunResult{Status: contract.StatusOK})
	if res.Status != contract.StatusInternalError || len(res.Artifacts) != 0 || st.deletes != 1 {
		t.Fatalf("res=%+v deleted=%d", res, st.deletes)
	}
}
