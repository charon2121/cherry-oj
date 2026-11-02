package preflight

import (
	"encoding/json"
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

func TestManifestLanguagesComeFromToolchainEntries(t *testing.T) {
	m := deploymentFixture()
	if got := toolchainLanguages(m); len(got) != 2 || got[0] != "c" || got[1] != "cpp" {
		t.Fatalf("languages = %q", got)
	}
}

// 仓库里每门语言一份包锁 locks/<语言>.lock.json：文件名与 language 字段一致，且都在注册表里，
// 否则按它构建的工具链装上节点后启动冒烟就会失败。
func TestRepositoryLocksAreRegisteredLanguages(t *testing.T) {
	locks, err := filepath.Glob(filepath.Join("..", "..", "..", "..", "..", "deploy", "sandbox-linux", "rootfs", "locks", "*.lock.json"))
	if err != nil || len(locks) == 0 {
		t.Fatalf("no package locks found: %v", err)
	}
	var declared []string
	for _, path := range locks {
		body, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		var lock struct {
			Language string `json:"language"`
		}
		if err := json.Unmarshal(body, &lock); err != nil {
			t.Fatalf("%s: %v", path, err)
		}
		name := strings.TrimSuffix(filepath.Base(path), ".lock.json")
		if lock.Language != name {
			t.Errorf("%s declares language %q", path, lock.Language)
		}
		declared = append(declared, name)
	}
	if _, err := validLanguages(declared); err != nil {
		t.Error(err)
	}
}
