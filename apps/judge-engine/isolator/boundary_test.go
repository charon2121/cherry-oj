//go:build linux && amd64

package isolator_test

import (
	"os/exec"
	"slices"
	"strings"
	"testing"
)

const isolatorPath = "cherry-oj/judge-engine/isolator"

// 本子树不在 internal/ 下，编译器不会拦住 sandbox 或 judge 引用它；这里检查的是
// 最终二进制的完整依赖闭包，间接引用也算。
func TestUnprivilegedBinariesDoNotLinkIsolator(t *testing.T) {
	for _, cmd := range []string{"./cmd/sandbox", "./cmd/judge"} {
		for _, dep := range dependencies(t, cmd) {
			if dep == isolatorPath || strings.HasPrefix(dep, isolatorPath+"/") {
				t.Errorf("%s 链接了特权包 %s", cmd, dep)
			}
		}
	}
	// 反向对照：特权二进制确实依赖本子树，证明上面的查询真的看到了依赖。
	if !slices.Contains(dependencies(t, "./cmd/sandbox-helper"), isolatorPath+"/daemon") {
		t.Fatal("go list 没有列出 sandbox-helper 对 daemon 的依赖，检查本身失效")
	}
}

func dependencies(t *testing.T, cmd string) []string {
	t.Helper()
	list := exec.Command("go", "list", "-deps", "-f", "{{.ImportPath}}", cmd)
	list.Dir = ".." // 模块根
	var stderr strings.Builder
	list.Stderr = &stderr
	out, err := list.Output()
	if err != nil {
		t.Fatalf("go list %s: %v\n%s", cmd, err, stderr.String())
	}
	return strings.Fields(string(out))
}
