package boundary_test

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/sandbox/cgroup"
)

func TestCgroupCapabilityRefusal(t *testing.T) {
	base, jobs := fixture(t)
	plain, err := os.MkdirTemp(base, "not-cgroup-")
	require(t, err)
	defer os.Remove(plain)
	if m, e := cgroup.Open(plain); e == nil {
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		_ = m.Close(ctx)
		t.Fatal("accepted ordinary filesystem")
	} else {
		t.Logf("ordinary filesystem refused: %v", e)
	}
	// Change only this test's empty jobs node; never touch a system ancestor.
	entries, err := os.ReadDir(jobs)
	require(t, err)
	for _, entry := range entries {
		if entry.IsDir() {
			t.Fatal("jobs not empty")
		}
	}
	for _, controller := range []string{"cpu", "memory", "pids"} {
		t.Run(controller, func(t *testing.T) {
			path := filepath.Join(jobs, "cgroup.subtree_control")
			require(t, os.WriteFile(path, []byte("-"+controller), 0600))
			defer func() { require(t, os.WriteFile(path, []byte("+"+controller), 0600)) }()
			m, e := cgroup.Open(jobs)
			if e == nil {
				ctx, cancel := context.WithTimeout(context.Background(), time.Second)
				defer cancel()
				_ = m.Close(ctx)
				t.Fatal("missing controller accepted")
			}
			if !strings.Contains(e.Error(), controller) {
				t.Fatal("unrelated failure", e)
			}
			t.Logf("refused: %v", e)
		})
	}
	m, err := cgroup.Open(jobs)
	require(t, err)
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	require(t, m.Close(ctx))
}
