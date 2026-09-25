//go:build linux && amd64

package execution_test

import (
	"bytes"
	"io"
	"os"
	"path/filepath"
	"testing"

	"cherry-oj/judge-engine/isolator/execution"

	"golang.org/x/sys/unix"
)

func TestOutputRejectsLinksAndSpecialFiles(t *testing.T) {
	p := t.TempDir()
	if err := os.WriteFile(filepath.Join(p, "plain"), []byte("value"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("plain", filepath.Join(p, "alias")); err != nil {
		t.Fatal(err)
	}
	if err := os.Link(filepath.Join(p, "plain"), filepath.Join(p, "hard")); err != nil {
		t.Fatal(err)
	}
	if err := unix.Mkfifo(filepath.Join(p, "fifo"), 0600); err != nil {
		t.Fatal(err)
	}
	root, err := os.Open(p)
	if err != nil {
		t.Fatal(err)
	}
	defer root.Close()
	for _, name := range []string{"plain", "hard", "alias", "fifo", "../escape"} {
		f, _, err := execution.OpenOutput(root, name)
		if err == nil {
			f.Close()
			t.Fatalf("接受 %s", name)
		}
	}
	if err := os.Remove(filepath.Join(p, "hard")); err != nil {
		t.Fatal(err)
	}
	f, n, err := execution.OpenOutput(root, "plain")
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	if n != 5 {
		t.Fatal(n)
	}
	data, err := io.ReadAll(f)
	if err != nil || !bytes.Equal(data, []byte("value")) {
		t.Fatal(err)
	}
}
