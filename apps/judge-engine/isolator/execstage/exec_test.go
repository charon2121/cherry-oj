//go:build linux && amd64

package execstage

import (
	"strings"
	"testing"

	"cherry-oj/judge-engine/isolator/seccomp"
	"cherry-oj/judge-engine/isolator/startup"
)

func TestExecSpecBoundary(t *testing.T) {
	valid := startup.ExecSpec{Path: "/work/main", Args: []string{"main"}, UID: 60001, GID: 60001, NoFile: 128, FileSizeBytes: 64 << 20, Profile: seccomp.Command, ErrorFD: 3}
	if err := validate(valid); err != nil {
		t.Fatal(err)
	}
	for _, mutate := range []func(*startup.ExecSpec){
		func(s *startup.ExecSpec) { s.UID = 0 }, func(s *startup.ExecSpec) { s.GID = -1 }, func(s *startup.ExecSpec) { s.Path = "main" },
		func(s *startup.ExecSpec) { s.Args = []string{"main\x00"} }, func(s *startup.ExecSpec) { s.Env = []string{strings.Repeat("a", 65<<10)} },
		func(s *startup.ExecSpec) { s.ErrorFD = 1 }, func(s *startup.ExecSpec) { s.NoFile = 65536 }, func(s *startup.ExecSpec) { s.Profile = seccomp.Supervisor },
	} {
		s := valid
		mutate(&s)
		if err := validate(s); err == nil {
			t.Fatal("invalid exec spec accepted")
		}
	}
}
