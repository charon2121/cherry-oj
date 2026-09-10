// 白盒断言等待队列已入列，避免用sleep猜测并发时序。
package pool

import (
	"context"
	"errors"
	"io"
	"io/fs"
	"sync/atomic"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/container"
	"cherry-oj/judge-engine/internal/sandbox/store"
)

type controlled struct {
	entered    chan struct{}
	closeError error
	closed     atomic.Bool
	ctx        context.Context
}

func (c *controlled) Start(ctx context.Context, _ container.Spec) (container.Process, error) {
	c.ctx = ctx
	close(c.entered)
	return c, nil
}
func (c *controlled) Wait(context.Context) (container.Usage, error) {
	if c.closeError == nil {
		<-c.ctx.Done()
	}
	return container.Usage{}, nil
}
func (*controlled) PutFile(string, io.Reader, fs.FileMode) error { return nil }
func (*controlled) GetFile(string) (io.ReadCloser, error)        { return nil, errors.New("unexpected file") }
func (c *controlled) Close() error                               { c.closed.Store(true); return c.closeError }
func TestQueueAndShutdown(t *testing.T) {
	st, e := store.NewDiskStoreWithRoot(t.TempDir() + "/store")
	if e != nil {
		t.Fatal(e)
	}
	defer st.Close()
	c := &controlled{entered: make(chan struct{})}
	p, e := New(st, Options{Parallelism: 1, QueueSize: 1, Factory: func() (container.Container, error) { return c, nil }})
	if e != nil {
		t.Fatal(e)
	}
	defer p.Close()
	done := make(chan struct{}, 2)
	run := func() { p.Run(context.Background(), contract.RunSpec{Command: []string{"true"}}); done <- struct{}{} }
	go run()
	<-c.entered
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
	if e := p.Close(); e != nil {
		t.Fatal(e)
	}
	<-done
	<-done
	if !c.closed.Load() {
		t.Fatal("shutdown did not close container")
	}
	if _, e := p.Run(context.Background(), contract.RunSpec{}); !errors.Is(e, ErrClosed) {
		t.Fatalf("got %v want closed", e)
	}
}
func TestCleanupFailureStopsAdmission(t *testing.T) {
	st, e := store.NewDiskStoreWithRoot(t.TempDir() + "/store")
	if e != nil {
		t.Fatal(e)
	}
	defer st.Close()
	injected := errors.New("cannot remove workspace")
	c := &controlled{entered: make(chan struct{}), closeError: injected}
	p, e := New(st, Options{Parallelism: 1, QueueSize: 1, Factory: func() (container.Container, error) { return c, nil }})
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

type panicContainer struct{ controlled }

func (*panicContainer) Start(context.Context, container.Spec) (container.Process, error) {
	panic("injected")
}
func TestPanicStillClosesContainer(t *testing.T) {
	st, e := store.NewDiskStoreWithRoot(t.TempDir() + "/store")
	if e != nil {
		t.Fatal(e)
	}
	defer st.Close()
	c := &panicContainer{}
	p, e := New(st, Options{Parallelism: 1, QueueSize: 1, Factory: func() (container.Container, error) { return c, nil }})
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
	if !c.closed.Load() || len(p.sem) != 0 || len(p.admitted) != 0 {
		t.Fatal("panic leaked execution ownership")
	}
}
