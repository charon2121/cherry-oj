package helper

import (
	"context"
	"errors"
	"os"
	"reflect"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/sandbox/cgroup"
)

type failedStartGroup struct {
	path              string
	steps             []string
	stopErr, closeErr error
}

func (g *failedStartGroup) File() (*os.File, error)            { return os.Open(g.path) }
func (g *failedStartGroup) Snapshot() (cgroup.Snapshot, error) { return cgroup.Snapshot{}, nil }
func (g *failedStartGroup) Stop(ctx context.Context) (cgroup.Snapshot, error) {
	g.steps = append(g.steps, "stop")
	if ctx.Err() != nil {
		return cgroup.Snapshot{}, ctx.Err()
	}
	return cgroup.Snapshot{}, g.stopErr
}
func (g *failedStartGroup) Close(ctx context.Context) error {
	g.steps = append(g.steps, "close")
	if ctx.Err() != nil {
		return ctx.Err()
	}
	return g.closeErr
}

// 传入普通目录作为 cgroup FD：clone3 必须拒绝，不创建真实 cgroup 或执行任何用户程序。
func TestFailedStartAlwaysReclaimsOwnedResources(t *testing.T) {
	for _, failure := range []string{"start", "stop", "close"} {
		t.Run(failure, func(t *testing.T) {
			state := t.TempDir()
			g := &failedStartGroup{path: t.TempDir()}
			injected := errors.New("injected cleanup failure")
			if failure == "stop" {
				g.stopErr = injected
			}
			if failure == "close" {
				g.closeErr = injected
			}
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			cancel()
			result, fatal := execute(ctx, testRequest(), strings.NewReader(""), Config{StateDir: state}, func(cgroup.Limits) (executionGroup, error) { return g, nil }, "/nonexistent-cherry-test-executable", cancel)
			defer result.Close()
			if result.Reason != "platform" {
				t.Fatal(result)
			}
			if !reflect.DeepEqual(g.steps, []string{"stop", "close"}) {
				t.Fatal(g.steps)
			}
			if failure != "start" && !errors.Is(fatal, injected) {
				t.Fatal("丢失清理错误", fatal)
			}
			entries, err := os.ReadDir(state)
			if err != nil {
				t.Fatal(err)
			}
			if failure != "stop" && len(entries) != 0 {
				t.Fatal("留下空挂载目录")
			}
		})
	}
}
