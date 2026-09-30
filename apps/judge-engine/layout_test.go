// 两棵服务子树之间的引用边界。judge、sandbox 都不放在 internal/ 下，编译器不会
// 拦住它们互相引用；这里检查每个服务二进制的完整依赖闭包，间接引用也算。
package judgeengine_test

import (
	"os"
	"os/exec"
	"slices"
	"strings"
	"testing"
)

const module = "cherry-oj/judge-engine/"

// 每个服务只能链接自己的子树和模块顶层的 internal/（协议定义与平台设施）：
//   - judge 只能通过 HTTP 使用 sandbox，不能进程内调用它的执行实现；
//   - sandbox 不理解判题，不能引用 judge。
//
// 特权部分是独立的 C 程序（apps/sandbox），不在本模块里，也就没有可链接的特权实现。
func TestServiceBinariesLinkOnlyTheirOwnSubtree(t *testing.T) {
	services := []struct {
		cmd, own  string
		forbidden []string
	}{
		{"./cmd/judge", "judge", []string{"execution"}},
		{"./cmd/sandbox", "execution", []string{"judge"}},
	}
	for _, s := range services {
		deps := dependencies(t, s.cmd)
		// 反向对照：二进制确实依赖自己的子树，证明查询真的看到了依赖，而不是返回了空集。
		if !slices.Contains(deps, module+s.own) {
			t.Fatalf("go list 没有列出 %s 对 %s 的依赖，检查本身失效", s.cmd, s.own)
		}
		for _, dep := range deps {
			for _, other := range s.forbidden {
				if dep == module+other || strings.HasPrefix(dep, module+other+"/") {
					t.Errorf("%s 链接了 %s 子树的 %s", s.cmd, other, dep)
				}
			}
		}
	}
}

// dependencies 按部署平台 linux/amd64 解析，在 macOS 上也检查部署时的依赖闭包。
func dependencies(t *testing.T, cmd string) []string {
	t.Helper()
	list := exec.Command("go", "list", "-deps", "-f", "{{.ImportPath}}", cmd)
	list.Env = append(os.Environ(), "GOOS=linux", "GOARCH=amd64")
	var stderr strings.Builder
	list.Stderr = &stderr
	out, err := list.Output()
	if err != nil {
		t.Fatalf("go list %s: %v\n%s", cmd, err, stderr.String())
	}
	return strings.Fields(string(out))
}
