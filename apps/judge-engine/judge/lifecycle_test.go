package judge

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net"
	"net/http"
	"testing"
	"time"
)

type failedListener struct{ err error }

func (l failedListener) Accept() (net.Conn, error) { return nil, l.err }
func (failedListener) Close() error                { return nil }
func (failedListener) Addr() net.Addr              { return &net.TCPAddr{} }

// 白盒注入监听失败，避免破坏进程的真实 FD；断言失败后无需外部取消即可退出心跳。
func TestServeFailureStopsRegistry(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	stopped := make(chan struct{})
	runNode := func(ctx context.Context) { defer close(stopped); <-ctx.Done() }
	want := errors.New("listener failed")
	done := make(chan error, 1)
	go func() {
		done <- serve(ctx, &http.Server{}, failedListener{want}, runNode, slog.New(slog.NewTextHandler(io.Discard, nil)))
	}()
	select {
	case err := <-done:
		if !errors.Is(err, want) {
			t.Fatalf("serve error = %v", err)
		}
	case <-time.After(3 * time.Second):
		cancel()
		<-done
		t.Fatal("service waits for external cancellation after Serve failure")
	}
	if ctx.Err() != nil {
		t.Fatal("test cancelled the caller instead of the service lifecycle")
	}
	select {
	case <-stopped:
	default:
		t.Fatal("service returned before registry stopped")
	}
}
