package launcher

import (
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/sandbox/policy"
)

func TestExecSpecBoundary(t *testing.T) {
	valid := ExecSpec{Path: "/work/main", Args: []string{"main"}, UID: 60001, GID: 60001, NoFile: 128, FileSizeBytes: 64 << 20, Profile: policy.Command, ErrorFD: 3}
	if err := valid.validate(); err != nil {
		t.Fatal(err)
	}
	for _, mutate := range []func(*ExecSpec){
		func(s *ExecSpec) { s.UID = 0 }, func(s *ExecSpec) { s.GID = -1 }, func(s *ExecSpec) { s.Path = "main" },
		func(s *ExecSpec) { s.Args = []string{"main\x00"} }, func(s *ExecSpec) { s.Env = []string{strings.Repeat("a", 65<<10)} },
		func(s *ExecSpec) { s.ErrorFD = 1 }, func(s *ExecSpec) { s.NoFile = 65536 }, func(s *ExecSpec) { s.Profile = policy.Supervisor },
	} {
		s := valid
		mutate(&s)
		if err := s.validate(); err == nil {
			t.Fatal("invalid exec spec accepted")
		}
	}
}
