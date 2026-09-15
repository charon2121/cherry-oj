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
	"cherry-oj/judge-engine/sandbox/internal/backend"
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

// lifecycleBackend 覆盖执行的各条退出路径；它总是交付一次声明过的产物，
// 用来验证「失败的执行不得发布产物」。
type lifecycleBackend struct {
	execErr    error
	facts      backend.Facts
	cancel     context.CancelFunc
	panicFirst bool
}

func (b *lifecycleBackend) Execute(_ context.Context, j backend.Job, sink backend.OutputSink) (backend.Facts, error) {
	if b.panicFirst {
		panic("execute panic")
	}
	if b.cancel != nil {
		b.cancel()
	}
	if b.execErr != nil {
		return b.facts, b.execErr
	}
	if sink != nil {
		for _, name := range j.Outputs {
			if err := sink(b.facts, name, strings.NewReader("artifact")); err != nil {
				return b.facts, err
			}
		}
	}
	return b.facts, nil
}

func TestRunClosesInputOnEveryExitPath(t *testing.T) {
	for _, path := range []string{"success", "execute-error", "wall", "cancel", "panic"} {
		t.Run(path, func(t *testing.T) {
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			b := &lifecycleBackend{}
			want := contract.StatusInternalError
			switch path {
			case "success":
				want = contract.StatusOK
			case "execute-error":
				b.execErr = errors.New("execute failed")
			case "wall":
				b.facts.Reason = hostexec.ReasonWall
				want = contract.StatusTimeLimitExceeded
			case "cancel":
				b.cancel = cancel
			case "panic":
				b.panicFirst = true
			}
			input := &closingInput{Reader: strings.NewReader("stdin")}
			st := &inputStore{input: input}
			var result contract.RunResult
			var recovered any
			func() {
				defer func() { recovered = recover() }()
				result, _ = Run(ctx, b, st, contract.RunSpec{Command: []string{"true"}, Stdin: &contract.FileSource{Ref: "input"}, Artifacts: []string{"out"}})
			}()
			if (recovered != nil) != b.panicFirst {
				t.Fatalf("unexpected panic: %v", recovered)
			}
			if !b.panicFirst && result.Status != want {
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
	result, _ := Run(context.Background(), &lifecycleBackend{}, st, contract.RunSpec{
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
