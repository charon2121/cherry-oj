package pool

import (
	"context"
	"errors"
	"io"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/internal/backend"
	"cherry-oj/judge-engine/sandbox/internal/runner"
	"cherry-oj/judge-engine/sandbox/internal/store"
)

// gateBackend 在交付产物之后、返回之前停住，用来观察「回收还没结束时容量有没有被归还」。
type gateBackend struct {
	entered      chan struct{}
	release      chan struct{}
	cleanupError error
	executes     atomic.Int32
}

func (b *gateBackend) Execute(_ context.Context, j backend.Job, sink backend.OutputSink) (backend.Facts, error) {
	b.executes.Add(1)
	if sink != nil {
		for _, name := range j.Outputs {
			if err := sink(backend.Facts{}, name, strings.NewReader("artifact")); err != nil {
				return backend.Facts{}, err
			}
		}
	}
	close(b.entered)
	<-b.release
	if b.cleanupError != nil {
		return backend.Facts{}, &backend.CleanupError{Err: b.cleanupError}
	}
	return backend.Facts{}, nil
}

type recordingStore struct {
	store.Store
	ref string
}

func (s *recordingStore) Put(r io.Reader) (string, error) {
	ref, err := s.Store.Put(r)
	if err == nil {
		s.ref = ref
	}
	return ref, err
}

// 结果和容量都必须等到回收结束才放出；回收未确认时既不能发布产物，也不能继续接单。
func TestRunHoldsResultAndCapacityUntilCleanupFinishes(t *testing.T) {
	for _, cleanupFails := range []bool{false, true} {
		name := map[bool]string{false: "success", true: "cleanup-failure"}[cleanupFails]
		t.Run(name, func(t *testing.T) {
			disk, err := store.NewDiskStoreWithRoot(t.TempDir() + "/store")
			if err != nil {
				t.Fatal(err)
			}
			defer disk.Close()
			st := &recordingStore{Store: disk}
			b := &gateBackend{entered: make(chan struct{}), release: make(chan struct{})}
			if cleanupFails {
				b.cleanupError = errors.New("workspace not reclaimed")
			}
			p, err := New(runner.New(b, st), Options{Parallelism: 1, QueueSize: 1})
			if err != nil {
				t.Fatal(err)
			}
			release := sync.OnceFunc(func() { close(b.release) })
			defer func() { release(); p.Close() }()

			type reply struct {
				result contract.RunResult
				err    error
			}
			done := make(chan reply, 1)
			go func() {
				result, err := p.Run(context.Background(), contract.RunSpec{Command: []string{"true"}, Artifacts: []string{"out"}})
				done <- reply{result, err}
			}()
			select {
			case <-b.entered:
			case <-time.After(time.Second):
				t.Fatal("回收阶段没有到达")
			}
			if len(p.sem) != 1 || len(p.admitted) != 1 {
				t.Fatal("回收结束前就归还了容量")
			}
			select {
			case result := <-done:
				t.Fatalf("回收结束前就返回了结果: %+v", result)
			default:
			}

			release()
			var got reply
			select {
			case got = <-done:
			case <-time.After(time.Second):
				t.Fatal("回收结束后 Run 没有返回")
			}
			if got.err != nil || b.executes.Load() != 1 || len(p.sem) != 0 || len(p.admitted) != 0 {
				t.Fatalf("reply=%+v executes=%d sem=%d admitted=%d", got, b.executes.Load(), len(p.sem), len(p.admitted))
			}
			if cleanupFails {
				if got.result.Status != contract.StatusInternalError || got.result.Artifacts != nil {
					t.Fatalf("回收失败却发布了成功结果: %+v", got.result)
				}
				if _, err := p.Run(context.Background(), contract.RunSpec{}); !errors.Is(err, ErrClosed) {
					t.Fatalf("回收未确认后仍在接单: %v", err)
				}
				if r, err := st.Get(st.ref); err == nil {
					r.Close()
					t.Fatal("产物没有回滚")
				}
			} else if got.result.Status != contract.StatusOK || got.result.Artifacts["out"] != st.ref || st.ref == "" {
				t.Fatalf("丢失了成功的产物: %+v", got.result)
			}
		})
	}
}
