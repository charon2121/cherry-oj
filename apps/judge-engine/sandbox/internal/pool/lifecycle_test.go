// 白盒断言等待队列已入列，避免用 sleep 猜测并发时序。
package pool

import (
	"context"
	"errors"
	"sync/atomic"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/internal/backend"
	"cherry-oj/judge-engine/sandbox/internal/store"
)

// blocking 一直执行到 ctx 取消为止，用来占住唯一的执行名额。
type blocking struct {
	entered      chan struct{}
	cleanupError error
	executes     atomic.Int32
	once         atomic.Bool
}

func (b *blocking) Execute(ctx context.Context, _ backend.Job, _ backend.OutputSink) (backend.Facts, error) {
	b.executes.Add(1)
	if b.once.CompareAndSwap(false, true) {
		close(b.entered)
	}
	if b.cleanupError != nil {
		return backend.Facts{}, &backend.CleanupError{Err: b.cleanupError}
	}
	<-ctx.Done()
	return backend.Facts{}, nil
}

func newStore(t *testing.T) store.Store {
	t.Helper()
	st, e := store.NewDiskStoreWithRoot(t.TempDir() + "/store")
	if e != nil {
		t.Fatal(e)
	}
	t.Cleanup(func() { st.Close() })
	return st
}

func TestQueueAndShutdown(t *testing.T) {
	b := &blocking{entered: make(chan struct{})}
	p, e := New(newStore(t), b, Options{Parallelism: 1, QueueSize: 1})
	if e != nil {
		t.Fatal(e)
	}
	defer p.Close()
	done := make(chan struct{}, 2)
	run := func() { p.Run(context.Background(), contract.RunSpec{Command: []string{"true"}}); done <- struct{}{} }
	go run()
	<-b.entered
	go run()
	deadline := time.After(time.Second)
	for len(p.admitted) != 2 {
		select {
		case <-deadline:
			t.Fatal("queue not entered")
		default:
			time.Sleep(time.Millisecond)
		}
	}
	if _, e := p.Run(context.Background(), contract.RunSpec{Command: []string{"true"}}); !errors.Is(e, ErrBusy) {
		t.Fatalf("got %v want busy", e)
	}
	// Close 取消在途执行并等它们收尾；返回后容量必须已经全部回到池里。
	if e := p.Close(); e != nil {
		t.Fatal(e)
	}
	<-done
	<-done
	if len(p.sem) != 0 || len(p.admitted) != 0 {
		t.Fatalf("shutdown leaked capacity: sem=%d admitted=%d", len(p.sem), len(p.admitted))
	}
	if _, e := p.Run(context.Background(), contract.RunSpec{}); !errors.Is(e, ErrClosed) {
		t.Fatalf("got %v want closed", e)
	}
}

// 回收未确认必须停止接单，并且 Close 要把原因原样带出来。
func TestCleanupFailureStopsAdmission(t *testing.T) {
	injected := errors.New("cannot remove workspace")
	b := &blocking{entered: make(chan struct{}), cleanupError: injected}
	p, e := New(newStore(t), b, Options{Parallelism: 1, QueueSize: 1})
	if e != nil {
		t.Fatal(e)
	}
	res, e := p.Run(context.Background(), contract.RunSpec{Command: []string{"true"}})
	if e != nil || res.Status != contract.StatusInternalError {
		t.Fatalf("res=%+v err=%v", res, e)
	}
	if _, e := p.Run(context.Background(), contract.RunSpec{}); !errors.Is(e, ErrClosed) {
		t.Fatalf("got %v", e)
	}
	if e := p.Close(); !errors.Is(e, injected) {
		t.Fatalf("close=%v", e)
	}
}

type panicking struct{ blocking }

func (*panicking) Execute(context.Context, backend.Job, backend.OutputSink) (backend.Facts, error) {
	panic("injected")
}

// 后端 panic 时容量仍须归还，否则并发数只减不增，最后整个服务卡死。
func TestPanicDoesNotLeakCapacity(t *testing.T) {
	p, e := New(newStore(t), &panicking{}, Options{Parallelism: 1, QueueSize: 1})
	if e != nil {
		t.Fatal(e)
	}
	defer p.Close()
	func() {
		defer func() {
			if recover() == nil {
				t.Error("expected panic")
			}
		}()
		p.Run(context.Background(), contract.RunSpec{Command: []string{"true"}})
	}()
	if len(p.sem) != 0 || len(p.admitted) != 0 {
		t.Fatalf("panic leaked capacity: sem=%d admitted=%d", len(p.sem), len(p.admitted))
	}
}
