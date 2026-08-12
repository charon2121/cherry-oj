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

func newTestPool(t *testing.T, parallelism int) *Pool {
	t.Helper()
	st, err := store.NewDiskStore()
	if err != nil {
		t.Fatal(err)
	}
	p := New(parallelism, st)
	t.Cleanup(func() { p.Close() })
	return p
}

func quickLimits() contract.Limits {
	return contract.Limits{
		ClockNs:        int64(2 * time.Second),
		StdoutMaxBytes: 65536,
		StderrMaxBytes: 65536,
	}
}

func TestParallelismIsCapped(t *testing.T) {
	const limit, requests = 2, 20

	p := newTestPool(t, limit)

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
				Limits:  quickLimits(),
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

func TestRunReleasesToken(t *testing.T) {
	const parallelism = 2
	p := newTestPool(t, parallelism)

	// 串行跑超过并发上限；令牌漏还的话，第 parallelism+1 次会永久卡住
	for i := range parallelism + 5 {
		res, err := p.Run(context.Background(), contract.RunSpec{
			Command: []string{"/bin/echo", "ok"},
			Limits:  quickLimits(),
		})
		if err != nil {
			t.Fatalf("run %d: %v", i, err)
		}
		if res.Status != contract.StatusOK {
			t.Fatalf("run %d: status=%s err=%s", i, res.Status, res.Error)
		}
	}
}

func TestReusesContainer(t *testing.T) {
	// parallelism=1：第二次一定借回同一个工作间（若 put 真的还池了）
	p := newTestPool(t, 1)

	res, err := p.Run(context.Background(), contract.RunSpec{
		Command: []string{"/bin/sh", "-c", "echo leftover > marker"},
		Limits:  quickLimits(),
	})
	if err != nil {
		t.Fatal(err)
	}
	if res.Status != contract.StatusOK {
		t.Fatalf("status=%s err=%s", res.Status, res.Error)
	}
	if len(p.containers) != 1 {
		t.Fatalf("containers=%d, want 1 (工作间应还回池子)", len(p.containers))
	}

	// Reset 过的话，marker 不该还在
	res, err = p.Run(context.Background(), contract.RunSpec{
		Command: []string{"/bin/sh", "-c", "test ! -e marker"},
		Limits:  quickLimits(),
	})
	if err != nil {
		t.Fatal(err)
	}
	if res.Status != contract.StatusOK {
		t.Fatalf("marker 仍在工作目录里（put 未 Reset？）status=%s stderr=%q", res.Status, res.Stderr)
	}
}
