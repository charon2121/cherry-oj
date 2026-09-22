package pool_test

import (
	"go/parser"
	"go/token"
	"os"
	"strconv"
	"strings"
	"testing"
)

// 容量管理可以消费执行结果，但不能重新接管存储或后端的装配。
func TestPoolDoesNotDependOnExecutionImplementations(t *testing.T) {
	entries, err := os.ReadDir(".")
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		name := entry.Name()
		if !strings.HasSuffix(name, ".go") || strings.HasSuffix(name, "_test.go") {
			continue
		}
		file, err := parser.ParseFile(token.NewFileSet(), name, nil, parser.ImportsOnly)
		if err != nil {
			t.Fatal(err)
		}
		for _, imported := range file.Imports {
			path, err := strconv.Unquote(imported.Path.Value)
			if err != nil {
				t.Fatal(err)
			}
			if strings.HasPrefix(path, "cherry-oj/judge-engine/sandbox/internal/") {
				t.Errorf("capacity package imports execution implementation %q in %s", path, name)
			}
		}
	}
}
