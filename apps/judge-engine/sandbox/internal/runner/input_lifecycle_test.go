package runner

import (
	"context"
	"errors"
	"io"
	"strings"
	"sync/atomic"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/sandbox/internal/container"
)

type closingInput struct {
	io.Reader
	closed atomic.Int32
	err    error
}

func (r *closingInput) Close() error { r.closed.Add(1); return r.err }

type inputStore struct {
	input         *closingInput
	puts, deletes int
	deleteErr     error
}

func (s *inputStore) Get(string) (io.ReadCloser, error) { return s.input, nil }
func (s *inputStore) Put(r io.Reader) (string, error) {
	if _, err := io.Copy(io.Discard, r); err != nil {
		return "", err
	}
	s.puts++
	return "artifact", nil
}
func (s *inputStore) Delete(string) error { s.deletes++; return s.deleteErr }

type inputLifecycleContainer struct {
	executionStub
	startErr, waitErr error
	usage             container.Usage
	cancel            context.CancelFunc
	panicStart        bool
}

func (c *inputLifecycleContainer) Start(context.Context, container.Spec) (container.Process, error) {
	if c.panicStart {
		panic("start panic")
	}
	return c, c.startErr
}
func (c *inputLifecycleContainer) Wait(context.Context) (container.Usage, error) {
	if c.cancel != nil {
		c.cancel()
	}
	return c.usage, c.waitErr
}

func TestRunClosesInputOnEveryExitPath(t *testing.T) {
	for _, path := range []string{"success", "start-error", "wait-error", "wall", "cancel", "panic"} {
		t.Run(path, func(t *testing.T) {
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			c := &inputLifecycleContainer{}
			want := contract.StatusInternalError
			switch path {
			case "success":
				want = contract.StatusOK
			case "start-error":
				c.startErr = errors.New("start failed")
			case "wait-error":
				c.waitErr = errors.New("wait failed")
			case "wall":
				c.usage.Reason = hostexec.ReasonWall
				want = contract.StatusTimeLimitExceeded
			case "cancel":
				c.cancel = cancel
			case "panic":
				c.panicStart = true
			}
			input := &closingInput{Reader: strings.NewReader("stdin")}
			st := &inputStore{input: input}
			var result contract.RunResult
			var recovered any
			func() {
				defer func() { recovered = recover() }()
				result = Run(ctx, c, st, contract.RunSpec{Command: []string{"true"}, Stdin: &contract.FileSource{Ref: "input"}, Artifacts: []string{"out"}})
			}()
			if (recovered != nil) != c.panicStart {
				t.Fatalf("unexpected panic: %v", recovered)
			}
			if !c.panicStart && result.Status != want {
				t.Fatalf("result=%+v, want %s", result, want)
			}
			if input.closed.Load() != 1 {
				t.Fatalf("input closed %d times", input.closed.Load())
			}
			if path != "success" && (st.puts != 0 || len(result.Artifacts) != 0) {
				t.Fatalf("failed execution published artifacts: %+v, puts=%d", result, st.puts)
			}
		})
	}
}

func TestInputCloseFailureRevokesArtifactsAndPreservesErrors(t *testing.T) {
	closeErr, deleteErr := errors.New("input close failed"), errors.New("artifact delete failed")
	input := &closingInput{Reader: strings.NewReader("stdin"), err: closeErr}
	st := &inputStore{input: input, deleteErr: deleteErr}
	result := Run(context.Background(), &inputLifecycleContainer{}, st, contract.RunSpec{
		Command: []string{"true"}, Stdin: &contract.FileSource{Ref: "input"}, Outputs: []string{"out"}, Artifacts: []string{"out"},
	})
	if result.Status != contract.StatusInternalError || result.Outputs != nil || result.Artifacts != nil {
		t.Fatalf("published result after close failure: %+v", result)
	}
	if st.puts != 1 || st.deletes != 1 || input.closed.Load() != 1 {
		t.Fatalf("puts=%d deletes=%d closes=%d", st.puts, st.deletes, input.closed.Load())
	}
	for _, err := range []error{closeErr, deleteErr} {
		if !strings.Contains(result.Error, err.Error()) {
			t.Fatalf("lost %v: %+v", err, result)
		}
	}
}
