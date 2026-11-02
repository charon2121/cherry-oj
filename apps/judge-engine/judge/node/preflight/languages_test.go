package preflight

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"cherry-oj/judge-engine/judge/config"
)

func TestDevelopmentNodesDeclareConfiguredLanguages(t *testing.T) {
	got, err := Languages(config.Settings{Node: config.Node{Languages: []string{"cpp"}}})
	if err != nil || len(got) != 1 || got[0] != "cpp" {
		t.Fatalf("Languages = %q, %v", got, err)
	}
}

// 声明的语言必须非空、格式正确、不重复，并且 judge 认得：否则拒绝上线，而不是悄悄少报。
func TestDeclaredLanguagesAreValidated(t *testing.T) {
	for _, declared := range [][]string{nil, {"cpp", "cpp"}, {"Cpp"}, {"cobol"}} {
		if _, err := validLanguages(declared); err == nil {
			t.Errorf("%q should be rejected", declared)
		}
	}
}

func TestLockLanguagesIgnoreTheRestOfTheLock(t *testing.T) {
	got, err := decodeLockLanguages(strings.NewReader(
		`{"version":1,"architecture":"amd64","languages":["cpp"],"packages":[{"file":"x.deb"}]}`))
	if err != nil || len(got) != 1 || got[0] != "cpp" {
		t.Fatalf("decode = %q, %v", got, err)
	}
	if got, err := decodeLockLanguages(strings.NewReader(`{"version":1}`)); err != nil || got != nil {
		t.Fatalf("lock without languages = %q, %v; Languages then rejects the empty list", got, err)
	}
	if _, err := decodeLockLanguages(strings.NewReader(strings.Repeat(" ", maxLockBytes+1))); err == nil {
		t.Fatal("oversized lock should be rejected")
	}
}

// 仓库里的包锁就是原生节点会读到的那份：它声明的语言必须都在注册表里，否则装上的节点起不来。
func TestRepositoryLocksDeclareRegisteredLanguages(t *testing.T) {
	locks, err := filepath.Glob(filepath.Join("..", "..", "..", "..", "..", "deploy", "sandbox-linux", "rootfs", "*.lock.json"))
	if err != nil || len(locks) == 0 {
		t.Fatalf("no package locks found: %v", err)
	}
	for _, path := range locks {
		file, err := os.Open(path)
		if err != nil {
			t.Fatal(err)
		}
		declared, err := decodeLockLanguages(file)
		file.Close()
		if err != nil {
			t.Fatalf("%s: %v", path, err)
		}
		if _, err := validLanguages(declared); err != nil {
			t.Errorf("%s: %v", path, err)
		}
	}
}
