// 两棵子树之间的引用边界。judge、execution 都不放在 internal/ 下，编译器不会拦住它们互相引用；
// 这里用 go list 检查依赖，间接引用也算。
package judgeengine_test

import (
	"os"
	"os/exec"
	"slices"
	"strings"
	"testing"
)

const module = "cherry-oj/judge-engine/"

// 每个服务二进制只能链接允许的子树和模块顶层的 internal/（协议定义与平台设施）：
//   - 执行层不理解判题，sandbox 服务不能链接 judge；
//   - judge 在本进程内装配执行层，可以链接 execution（见 TestOnlyJudgeAssemblyImportsExecution）。
//
// 特权部分是独立的 C 程序（apps/sandbox），不在本模块里，也就没有可链接的特权实现。
func TestServiceBinariesLinkOnlyTheirOwnSubtree(t *testing.T) {
	services := []struct {
		cmd, own  string
		forbidden []string
	}{
		{"./cmd/judge", "judge", nil},
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

// 执行层不懂判题：execution 下任何包都不得引用 judge。judge 这边只有装配处（judge 根包与
// 放配置类型的 judge/config）可以直接引用执行层；判题编排、比对、节点等包只面对自己声明的
// 窄接口，拿不到执行层的实现细节。
func TestOnlyJudgeAssemblyImportsExecution(t *testing.T) {
	allowed := []string{module + "judge", module + "judge/config"}
	checked := 0
	for pkg, imports := range packageImports(t, "./judge/...", "./execution/...") {
		checked++
		for _, imp := range imports {
			switch {
			case strings.HasPrefix(pkg, module+"execution") && (imp == module+"judge" || strings.HasPrefix(imp, module+"judge/")):
				t.Errorf("%s imports judge package %s", pkg, imp)
			case strings.HasPrefix(pkg, module+"judge") && (imp == module+"execution" || strings.HasPrefix(imp, module+"execution/")) &&
				!slices.Contains(allowed, pkg):
				t.Errorf("%s imports execution package %s; only the judge assembly may", pkg, imp)
			}
		}
	}
	if checked < 10 {
		t.Fatalf("go list returned only %d packages; the check itself is broken", checked)
	}
}

// packageImports 列出给定模式下每个包的直接引用（不含测试文件）。
func packageImports(t *testing.T, patterns ...string) map[string][]string {
	t.Helper()
	args := append([]string{"list", "-f", "{{.ImportPath}} {{join .Imports \" \"}}"}, patterns...)
	list := exec.Command("go", args...)
	list.Env = append(os.Environ(), "GOOS=linux", "GOARCH=amd64")
	var stderr strings.Builder
	list.Stderr = &stderr
	out, err := list.Output()
	if err != nil {
		t.Fatalf("go list: %v\n%s", err, stderr.String())
	}
	result := map[string][]string{}
	for _, line := range strings.Split(strings.TrimSpace(string(out)), "\n") {
		fields := strings.Fields(line)
		result[fields[0]] = fields[1:]
	}
	return result
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
