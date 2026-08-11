package pool

import (
	"context"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/store"
)

func TestParallelismIsCapped(t *testing.T) {
	const limit, requests = 2, 20

	st, err := store.NewDiskStore()
	if err != nil {
		t.Fatal(err)
	}

	p := New(limit, st)
	t.Cleanup(func() { p.Close() })

	var peak atomic.Int64

	errCh := make(chan error, requests)
	stop := make(chan struct{})

	var sampler sync.WaitGroup
	sampler.Go(func() {
		for {
			select {
			case <-stop:
				return
			default:
				// len(sem) = 已占用的令牌 = 真正在跑的并发数（不含还在排队的）
				n := int64(len(p.sem))
				for {
					old := peak.Load()
					if n <= old {
						break
					}
					if peak.CompareAndSwap(old, n) {
						break
					}
				}
				time.Sleep(time.Millisecond)
			}
		}
	})

	var wg sync.WaitGroup
	for range requests {
		wg.Go(func() {
			_, err := p.Run(context.Background(), contract.RunSpec{
				Command: []string{"/bin/sh", "-c", "sleep 0.05"},
				Limits: contract.Limits{
					ClockNs:        int64(2 * time.Second),
					StdoutMaxBytes: 65536,
					StderrMaxBytes: 65536,
				},
			})
			if err != nil {
				errCh <- err // 子 goroutine 里不能调 t.Fatal
			}
		})
	}

	wg.Wait()
	close(stop)
	sampler.Wait()
	close(errCh)
	for err := range errCh {
		t.Fatal(err) // 回到测试 goroutine 再报
	}

	if got := peak.Load(); got > limit {
		t.Errorf("同时在跑 %d 个，超过上限 %d", got, limit)
	}
	if peak.Load() < 2 {
		t.Errorf("峰值只有 %d，根本没并发起来，这个测试没测到东西", peak.Load())
	}
}
