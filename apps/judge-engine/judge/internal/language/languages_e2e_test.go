package language_test

import (
	"bytes"
	"flag"
	"os/exec"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/judge/internal/language"
)

// 可信功能夹具需容纳CI首次加载工具链的等待，依据见WORK-050/TASK-115。
// 诊断仍显式测原5秒；生产、运行期限和严格资源回归不受影响。
var compileClock = flag.Duration("language-compile-clock", 15*time.Second, "trusted language fixture compile deadline (0 < deadline <= 30s)")

// 集成测试：用真的 container 跑一遍 registry 里的命令，
// 编译 → 收产物 → **换一个干净工作间** → 运行。
//
// 中间换工作间这一步是关键——它模拟的正是 flow 的两次 /run（编译一次、
// 每个测试点各一次），产物要靠 store 跨越两次运行。上面那些单测只能检查
// 配置「自洽」，只有这里能检查配置「真的能跑」，尤其是产物收全了没有。
//
// 依赖本机工具链，缺哪个就跳过哪个——CI 上装了什么就测什么。
func runLang(t *testing.T, langName, source, stdin string) string {
	t.Helper()
	lang, ok := language.Get(langName)
	if !ok {
		t.Fatalf("Get(%s)", langName)
	}
	// 只探测「要从 PATH 找的程序」；工作目录里的产物不在 PATH 上，不用探
	if lang.NeedsCompile() {
		if _, err := exec.LookPath(lang.Compile[0]); err != nil {
			t.Skipf("环境里没有 %s", lang.Compile[0])
		}
	}
	if lang.Run[0] != lang.CompiledArtifact {
		if _, err := exec.LookPath(lang.Run[0]); err != nil {
			t.Skipf("环境里没有 %s", lang.Run[0])
		}
	}

	build := newWorkspace(t)
	build.put(t, lang.SourceName, strings.NewReader(source), 0o644)

	var artifact []byte
	if lang.NeedsCompile() {
		var cerr bytes.Buffer
		if *compileClock <= 0 || *compileClock > 30*time.Second {
			t.Fatal("language-compile-clock must be within (0, 30s]")
		}
		u, err := build.run(lang.Compile, nil, nil, &cerr, *compileClock)
		t.Logf("compile language=%s usage=%+v waitError=%v", langName, u, err)
		if err != nil || u.ExitCode != 0 {
			t.Fatalf("编译失败 usage=%+v waitError=%v stderr=%s", u, err, cerr.String())
		}
		artifact = build.get(t, lang.CompiledArtifact)
	}

	// ★ 换一个干净工作间：只把声明的产物搬过去。
	// 产物没收全的话，这里就会暴露出来。
	run := newWorkspace(t)
	if lang.NeedsCompile() {
		run.put(t, lang.CompiledArtifact, bytes.NewReader(artifact), 0o755)
	} else {
		run.put(t, lang.SourceName, strings.NewReader(source), 0o644)
	}

	var out, errb bytes.Buffer
	u, err := run.run(lang.Run, strings.NewReader(stdin), &out, &errb, 0)
	t.Logf("run language=%s usage=%+v waitError=%v", langName, u, err)
	if err != nil {
		t.Fatal(err)
	}
	if u.ExitCode != 0 {
		t.Fatalf("运行失败 exit=%d stderr=%s", u.ExitCode, errb.String())
	}
	return out.String()
}

func TestEndToEndCpp(t *testing.T) {
	src := "#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<std::endl;}"
	if got := runLang(t, "cpp", src, "1 2\n"); got != "3\n" {
		t.Errorf("got %q want %q", got, "3\n")
	}
}

func TestEndToEndPython(t *testing.T) {
	src := "a,b=map(int,input().split())\nprint(a+b)\n"
	if got := runLang(t, "python", src, "1 2\n"); got != "3\n" {
		t.Errorf("got %q want %q", got, "3\n")
	}
}

// ★ 带内部类：只收 Main.class 的话这里会 NoClassDefFoundError
func TestEndToEndJavaWithInnerClass(t *testing.T) {
	src := `import java.util.*;
public class Main {
    static class Pair { long a, b; Pair(long a, long b){this.a=a;this.b=b;} long sum(){return a+b;} }
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println(new Pair(sc.nextLong(), sc.nextLong()).sum());
    }
}`
	if got := runLang(t, "java", src, "1 2\n"); got != "3\n" {
		t.Errorf("got %q want %q", got, "3\n")
	}
}
