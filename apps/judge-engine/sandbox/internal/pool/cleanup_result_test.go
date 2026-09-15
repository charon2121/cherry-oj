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
	"cherry-oj/judge-engine/sandbox/internal/container"
	"cherry-oj/judge-engine/sandbox/internal/store"
)

type closeGateContainer struct {
	controlled
	closing chan struct{}
	release chan struct{}
	closes  atomic.Int32
}

func (c *closeGateContainer) Start(context.Context, container.Spec) (container.Process, error) {
	return c, nil
}

func (c *closeGateContainer) Wait(context.Context) (container.Usage, error) {
	return container.Usage{}, nil
}
func (c *closeGateContainer) GetFile(string) (io.ReadCloser, error) {
	return io.NopCloser(strings.NewReader("artifact")), nil
}
func (c *closeGateContainer) Close() error {
	if c.closes.Add(1) == 1 {
		close(c.closing)
	}
	<-c.release
	return c.closeError
}

type recordingStore struct {
	store.Store
	ref string
}

func (s *recordingStore) Put(r io.Reader) (string, error) {
	ref, err := s.Store.Put(r)
	s.ref = ref
	return ref, err
}

func TestRunHoldsResultAndCapacityUntilContainerCloses(t *testing.T) {
	for _, closeFails := range []bool{false, true} {
		name := "success"
		if closeFails {
			name = "close-failure"
		}
		t.Run(name, func(t *testing.T) {
			disk, err := store.NewDiskStoreWithRoot(t.TempDir() + "/store")
			if err != nil {
				t.Fatal(err)
			}
			defer disk.Close()
			st := &recordingStore{Store: disk}
			c := &closeGateContainer{controlled: controlled{entered: make(chan struct{})}, closing: make(chan struct{}), release: make(chan struct{})}
			if closeFails {
				c.closeError = errors.New("container close failed")
			}
			p, err := New(st, Options{Parallelism: 1, QueueSize: 1, Factory: func() (container.Container, error) { return c, nil }})
			if err != nil {
				t.Fatal(err)
			}
			release := sync.OnceFunc(func() { close(c.release) })
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
			case <-c.closing:
			case <-time.After(time.Second):
				t.Fatal("container close was not reached")
			}
			if len(p.sem) != 1 || len(p.admitted) != 1 {
				t.Fatal("capacity returned before cleanup")
			}
			select {
			case result := <-done:
				t.Fatalf("result returned before cleanup: %+v", result)
			default:
			}
			release()
			var got reply
			select {
			case got = <-done:
			case <-time.After(time.Second):
				t.Fatal("Run did not finish after cleanup")
			}
			if got.err != nil || c.closes.Load() != 1 || len(p.sem) != 0 || len(p.admitted) != 0 {
				t.Fatalf("reply=%+v closes=%d sem=%d admitted=%d", got, c.closes.Load(), len(p.sem), len(p.admitted))
			}
			if closeFails {
				if got.result.Status != contract.StatusInternalError || got.result.Artifacts != nil {
					t.Fatalf("cleanup failure published success: %+v", got.result)
				}
				if _, err := p.Run(context.Background(), contract.RunSpec{}); !errors.Is(err, ErrClosed) {
					t.Fatalf("pool still accepts work: %v", err)
				}
				if r, err := st.Get(st.ref); err == nil {
					r.Close()
					t.Fatal("artifact was not rolled back")
				}
			} else if got.result.Status != contract.StatusOK || got.result.Artifacts["out"] != st.ref || st.ref == "" {
				t.Fatalf("lost successful artifact: %+v", got.result)
			}
		})
	}
}
