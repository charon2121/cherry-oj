// 白盒测试（package language 而非 language_test）：
// 一致性检查要遍历整个 registry，而它是非导出的。若改成黑盒、
// 只检查硬编码的那几个名字，将来新加一门语言就检查不到了——
// 而「新加语言时配错」正是这类检查最该拦住的场景。
package language

import (
	"slices"
	"strings"
	"testing"
)

func TestGet(t *testing.T) {
	tests := []struct {
		name             string
		sourceName       string
		compiledArtifact string
		needsCompile     bool
	}{
		{"cpp", "Main.cpp", "Main", true},
		{"python", "Main.py", "", false},
		{"java", "Main.java", "Main.jar", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			lang, ok := Get(tt.name)
			if !ok {
				t.Fatalf("Get(%q) 没找到", tt.name)
			}
			if lang.Name != tt.name {
				t.Errorf("Name = %q, want %q", lang.Name, tt.name)
			}
			if lang.SourceName != tt.sourceName {
				t.Errorf("SourceName = %q, want %q", lang.SourceName, tt.sourceName)
			}
			if lang.CompiledArtifact != tt.compiledArtifact {
				t.Errorf("CompiledArtifact = %q, want %q", lang.CompiledArtifact, tt.compiledArtifact)
			}
			if lang.NeedsCompile() != tt.needsCompile {
				t.Errorf("NeedsCompile() = %v, want %v", lang.NeedsCompile(), tt.needsCompile)
			}
			if len(lang.Run) == 0 {
				t.Error("Run 不能为空——不然没法跑")
			}
		})
	}
}

// 未知语言必须明确说「没有」，而不是返回一个零值假装成功。
// 零值 Language 的 Run 是空的，真拿去跑会得到一句 "empty command"，
// 离病因（语言名写错了）已经很远。
func TestGetUnknown(t *testing.T) {
	for _, name := range []string{"", "c++", "CPP", "golang", "cpp "} {
		t.Run(name, func(t *testing.T) {
			lang, ok := Get(name)
			if ok {
				t.Fatalf("Get(%q) 不该找到", name)
			}
			if lang.Name != "" || len(lang.Compile) != 0 || len(lang.Run) != 0 {
				t.Errorf("没找到时应当返回零值，得到 %+v", lang)
			}
		})
	}
}

// ★ Get 返回的必须是独立副本。
//
// Language 是按值返回的，看着安全，但 Compile / Run 是切片——切片头是值，
// 底层数组是共享的。调用方改一下（或者 append 到一个 cap 有富余的切片上），
// 改的就是全局 registry，整个进程后续每次 Get 都拿到被污染的命令。
//
// 这类 bug 极难查：污染发生在一次判题里，症状出现在之后所有判题上。
func TestGetReturnsIndependentCopy(t *testing.T) {
	first, ok := Get("cpp")
	if !ok {
		t.Fatal("Get(cpp) 没找到")
	}
	original := slices.Clone(first.Compile)

	// 模拟调用方无意的一次改写
	first.Compile[0] = "definitely-not-a-compiler"
	first.Run[0] = "definitely-not-a-binary"

	second, _ := Get("cpp")
	if !slices.Equal(second.Compile, original) {
		t.Errorf("registry 被上一个调用方改掉了: Compile = %v, want %v",
			second.Compile, original)
	}
	if second.Run[0] == "definitely-not-a-binary" {
		t.Errorf("registry 被上一个调用方改掉了: Run = %v", second.Run)
	}
}

// registry 里每一条都要自洽。遍历而不是硬编码，这样新加一门语言时也会被检查到。
func TestRegistryConsistency(t *testing.T) {
	for key, lang := range registry {
		t.Run(key, func(t *testing.T) {
			// map 的 key 和 Name 必须一致，否则 Get 出来的 Name 会指向另一门语言
			if lang.Name != key {
				t.Errorf("registry[%q].Name = %q，两者必须一致", key, lang.Name)
			}
			if lang.SourceName == "" {
				t.Error("SourceName 不能为空——inputs 要靠它决定源码写成什么文件名")
			}
			if len(lang.Run) == 0 {
				t.Error("Run 不能为空")
			}

			if lang.NeedsCompile() {
				if lang.CompiledArtifact == "" {
					t.Error("需要编译却没声明 CompiledArtifact——flow 不知道该收哪个产物")
				}
				// 同一份信息写了两遍（SourceName 和 Compile 里的文件名），
				// 容易改一处忘一处，用测试钉住。
				// 用 Join 而不是 Contains：java 走的是 sh -c "javac Main.java && ..."，
				// 源码名嵌在一个字符串参数里，不是独立元素。
				if !strings.Contains(strings.Join(lang.Compile, " "), lang.SourceName) {
					t.Errorf("Compile %v 里没出现 SourceName %q——编译命令编的不是这份源码",
						lang.Compile, lang.SourceName)
				}
				// 产物名也要出现在编译命令里，否则编译器根本不会产出它
				if !strings.Contains(strings.Join(lang.Compile, " "), lang.CompiledArtifact) {
					t.Errorf("Compile %v 里没出现 CompiledArtifact %q——编译器不会产出这个文件",
						lang.Compile, lang.CompiledArtifact)
				}
				// 产物必须真的被用起来，否则收了个寂寞
				if !strings.Contains(strings.Join(lang.Run, " "), strings.TrimSuffix(lang.CompiledArtifact, ".jar")) &&
					!slices.Contains(lang.Run, lang.CompiledArtifact) {
					t.Errorf("Run %v 没用上 CompiledArtifact %q", lang.Run, lang.CompiledArtifact)
				}
			} else if lang.CompiledArtifact != "" {
				t.Errorf("不需要编译却声明了 CompiledArtifact %q——flow 会去收一个永远不存在的产物",
					lang.CompiledArtifact)
			}
		})
	}
}

// 编译命令里 -o 后面跟的必须就是声明的产物名。
// 对不上的话，g++ 报告成功、flow 却收不到产物，会被判成 SE——
// 而错误信息指向的是判题机，不是这条配错的命令。
func TestCompileOutputMatchesArtifact(t *testing.T) {
	for key, lang := range registry {
		if !lang.NeedsCompile() {
			continue
		}
		t.Run(key, func(t *testing.T) {
			i := slices.Index(lang.Compile, "-o")
			if i < 0 {
				t.Skipf("%s 的编译命令不用 -o 指定产物，跳过", key)
			}
			if i+1 >= len(lang.Compile) {
				t.Fatalf("-o 后面没有参数: %v", lang.Compile)
			}
			if got := lang.Compile[i+1]; got != lang.CompiledArtifact {
				t.Errorf("-o %q 和 CompiledArtifact %q 对不上", got, lang.CompiledArtifact)
			}
		})
	}
}

// 命令里不该出现路径：
//
//   - "./x"：M1a 的 container 规则是「命令名不含 / 且该文件存在于 workDir
//     时才解析成绝对路径」。写成 "./x" 绕过了这条，变成相对沙箱进程 CWD 去找。
//   - "/usr/bin/xxx"：绕过 PATH，写死了某台机器的布局。macOS 上
//     /usr/bin/python3 是系统自带的 3.9，而部署环境装的可能是 3.12——
//     判题用的解释器和用户本地不是同一个。
//
// 一律写裸名字，交给 PATH 解析；要指定版本就在部署环境里配 PATH。
func TestCommandsUsePathLookup(t *testing.T) {
	for key, lang := range registry {
		t.Run(key, func(t *testing.T) {
			check := func(field string, args []string) {
				if len(args) == 0 {
					return
				}
				head := args[0] // 只有第 0 个是「要执行的程序」，后面是参数
				if strings.HasPrefix(head, "./") {
					t.Errorf("%s[0] = %q——工作目录内的文件直接写名字即可", field, head)
				}
				if strings.HasPrefix(head, "/") {
					t.Errorf("%s[0] = %q——别硬编码绝对路径，交给 PATH 解析", field, head)
				}
			}
			check("Run", lang.Run)
			check("Compile", lang.Compile)
		})
	}
}

func TestNeedsCompile(t *testing.T) {
	// 零值 Language 没有编译命令
	if (Language{}).NeedsCompile() {
		t.Error("零值 Language 不该需要编译")
	}
	if !(Language{Compile: []string{"gcc"}}).NeedsCompile() {
		t.Error("有 Compile 就该返回 true")
	}
	if (Language{Compile: []string{}}).NeedsCompile() {
		t.Error("空切片不算需要编译")
	}
}
