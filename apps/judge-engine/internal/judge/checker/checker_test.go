package checker_test

import (
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/judge/checker"
)

// opt(true) = 严格（空白不一致判 PE），opt(false) = 宽松（判 AC）
func opt(strict bool) checker.Options {
	return checker.Options{StrictWhitespace: strict}
}

func compare(t *testing.T, o checker.Options, got, want string) (contract.Verdict, contract.Diff) {
	t.Helper()
	v, d, err := checker.Compare(o, strings.NewReader(got), strings.NewReader(want))
	if err != nil {
		t.Fatalf("Compare: %v", err)
	}
	return v, d
}

// token 序列一致 → 不是 WA；不一致 → WA。这一层和严不严格无关。
func TestTokenEquality(t *testing.T) {
	tests := []struct {
		name   string
		got    string
		want   string
		wantWA bool
	}{
		{"完全相同", "3\n", "3\n", false},
		{"多行相同", "1\n2\n3\n", "1\n2\n3\n", false},
		{"两边都空", "", "", false},
		{"只有空白 vs 空", "  \n\n", "", false},

		{"值不同", "4\n", "3\n", true},
		{"选手少一行", "1\n", "1\n2\n", true},
		{"选手多一行", "1\n2\n", "1\n", true},
		{"选手是前缀", "1\n", "12\n", true},
		{"标准答案是前缀", "12\n", "1\n", true},
		{"选手没输出", "", "3\n", true},
		{"标准答案为空但选手有输出", "3\n", "", true},
		{"顺序不同", "1 2\n", "2 1\n", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// 严格/宽松两档下，「是不是 WA」必须一致
			for _, pe := range []bool{false, true} {
				v, _ := compare(t, opt(pe), tt.got, tt.want)
				isWA := v == contract.VerdictWA
				if isWA != tt.wantWA {
					t.Errorf("strictWhitespace=%v: verdict=%s, wantWA=%v", pe, v, tt.wantWA)
				}
			}
		})
	}
}

// ★ 空白差异一律被**检测**到，判 AC 还是 PE 交给配置。
// 这是本包最核心的一条：checker 报事实，配置做定性。
func TestWhitespaceIsDetectedAndPolicyDecides(t *testing.T) {
	tests := []struct {
		name     string
		got      string
		want     string
		wsDiffer bool // 空白排布是否不一致
	}{
		{"一模一样", "3\n", "3\n", false},
		{"行内空格相同", "1 2\n", "1 2\n", false},

		{"缺末尾换行", "3", "3\n", true},
		{"多末尾换行", "3\n\n\n", "3\n", true},
		{"行尾空格", "3   \n", "3\n", true},
		{"Windows 换行", "3\r\n", "3\n", true},
		{"行内空格数量不同", "1  2\n", "1 2\n", true},
		{"空格 vs 制表符", "1\t2\n", "1 2\n", true},
		{"换行 vs 空格", "1\n2\n", "1 2\n", true},
		{"行首缩进", "  3\n", "3\n", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// 关掉 PE：空白差异一律 AC
			if v, _ := compare(t, opt(false), tt.got, tt.want); v != contract.VerdictAC {
				t.Errorf("strictWhitespace=false 时应为 AC，得到 %s", v)
			}

			// 打开 PE：空白有差异就是 PE，没差异还是 AC
			want := contract.VerdictAC
			if tt.wsDiffer {
				want = contract.VerdictPE
			}
			if v, _ := compare(t, opt(true), tt.got, tt.want); v != want {
				t.Errorf("strictWhitespace=true 时应为 %s，得到 %s", want, v)
			}
		})
	}
}

// WA 优先于 PE：既有 token 差异又有空白差异时，报的必须是 WA。
// 反过来的话，用户会以为「只是格式问题」，其实答案根本不对。
func TestWABeatsPE(t *testing.T) {
	v, _ := compare(t, opt(true), "1  9\n", "1 2\n")
	if v != contract.VerdictWA {
		t.Errorf("verdict=%s，token 不同就该是 WA，不该被 PE 盖过", v)
	}
}

// ★ 行号必须指向第一处不同。算错的话用户照着去查只会更迷惑，
// 而且是**无声的**——verdict 还是对的。
func TestDiffLine(t *testing.T) {
	tests := []struct {
		name string
		got  string
		want string
		line int
	}{
		{"第一行就不同", "9\n2\n3\n", "1\n2\n3\n", 1},
		{"第二行不同", "1\n9\n3\n", "1\n2\n3\n", 2},
		{"第三行不同", "1\n2\n9\n", "1\n2\n3\n", 3},
		{"选手少了第三行", "1\n2\n", "1\n2\n3\n", 3},
		{"选手多了第三行", "1\n2\n3\n", "1\n2\n", 3},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v, d := compare(t, opt(false), tt.got, tt.want)
			if v != contract.VerdictWA {
				t.Fatalf("verdict=%s want WA", v)
			}
			if d.Line != tt.line {
				t.Errorf("Line=%d want %d", d.Line, tt.line)
			}
		})
	}
}

// PE 的行号同样要指向第一处空白差异，
// 不能是「跳完空白之后」的位置——那可能已经跨过好几个换行了。
func TestPEDiffLine(t *testing.T) {
	tests := []struct {
		name string
		got  string
		want string
		line int
	}{
		{"第一行行尾多空格", "3   \n", "3\n", 1},
		{"第一行行内空格多", "1  2\n", "1 2\n", 1},
		{"缺末尾换行", "3", "3\n", 1},
		{"末尾多空行", "1\n2\n\n\n", "1\n2\n", 3},
		{"第二行才有差异", "1\n2  3\n", "1\n2 3\n", 2},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v, d := compare(t, opt(true), tt.got, tt.want)
			if v != contract.VerdictPE {
				t.Fatalf("verdict=%s want PE", v)
			}
			if d.Line != tt.line {
				t.Errorf("Line=%d want %d", d.Line, tt.line)
			}
		})
	}
}

// Diff 里要带上出问题那一行的内容，用户才知道自己输出了什么。
// checker 总是把 Want 填上——要不要回传给用户由 flow 按 revealExpected 决定。
func TestDiffExcerpt(t *testing.T) {
	v, d := compare(t, opt(false), "1\nhello world\n3\n", "1\nhello there\n3\n")
	if v != contract.VerdictWA {
		t.Fatalf("verdict=%s", v)
	}
	if d.Line != 2 {
		t.Errorf("Line=%d want 2", d.Line)
	}
	if d.Got != "hello world" {
		t.Errorf("Got=%q want %q", d.Got, "hello world")
	}
	if d.Want != "hello there" {
		t.Errorf("Want=%q want %q", d.Want, "hello there")
	}
}

// AC 时不该带 Diff —— 没有「第一处不同」这回事
func TestNoDiffOnAC(t *testing.T) {
	_, d := compare(t, opt(true), "1 2\n", "1 2\n")
	if d.Line != 0 || d.Got != "" || d.Want != "" {
		t.Errorf("AC 却带了 Diff: %+v", d)
	}
}

// Options 零值 = 宽松。调用方漏填不该让所有格式不规范的提交全变 PE。
func TestZeroOptionsIsLenient(t *testing.T) {
	v, _ := compare(t, checker.Options{}, "3   \n", "3\n")
	if v != contract.VerdictAC {
		t.Errorf("verdict=%s，零值应当是宽松（AC）", v)
	}
}

// ★ 空白差异**永远不会**变成 WA —— 无论严不严格。
// 判成 WA 会让选手去查一个根本不存在的算法错误。
func TestWhitespaceNeverBecomesWA(t *testing.T) {
	pairs := [][2]string{
		{"3", "3\n"}, {"3\n\n\n", "3\n"}, {"3   \n", "3\n"}, {"3\r\n", "3\n"},
		{"1  2\n", "1 2\n"}, {"1\t2\n", "1 2\n"}, {"1\n2\n", "1 2\n"}, {"  3\n", "3\n"},
		{"\n\n1 2\n\n", "1 2"},
	}
	for _, p := range pairs {
		for _, strict := range []bool{false, true} {
			v, _ := compare(t, opt(strict), p[0], p[1])
			if v == contract.VerdictWA {
				t.Errorf("%q vs %q（strict=%v）判成了 WA —— 内容是对的，只是排版不同",
					p[0], p[1], strict)
			}
		}
	}
}

// ★ 超长行不能出问题。
// 用 bufio.Scanner 的实现会在这里撞上默认 64KB 上限报 token too long，
// 然后被上层当成 SE —— 而题目本身没有任何毛病。
func TestVeryLongLine(t *testing.T) {
	long := strings.Repeat("1234567890", 200_000) // 2 MB 一行

	if v, _ := compare(t, opt(true), long+"\n", long+"\n"); v != contract.VerdictAC {
		t.Errorf("超长行相同应为 AC，得到 %s", v)
	}
	if v, _ := compare(t, opt(false), long+"9\n", long+"\n"); v != contract.VerdictWA {
		t.Errorf("超长行不同应为 WA，得到 %s", v)
	}
}

// 截断片段不能把多字节字符劈成两半，否则前端显示成乱码方块
func TestExcerptKeepsRuneBoundary(t *testing.T) {
	long := strings.Repeat("中", 500) // 每个 3 字节，远超 excerpt 上限

	_, d := compare(t, opt(false), long+"A\n", long+"B\n")
	for _, s := range []string{d.Got, d.Want} {
		for i, r := range s {
			if r == '�' {
				t.Errorf("片段里出现了半个字符（位置 %d）: %q", i, s)
				break
			}
		}
	}
}

// 大量数据下内存要恒定：这条不直接断言内存，而是确认几 MB 的输入能正常跑完，
// 且没有因为一次性读取而变慢/爆掉。真正的保证在于实现里没有 ReadAll。
func TestLargeInputStreams(t *testing.T) {
	var sb strings.Builder
	for i := range 200_000 {
		sb.WriteString("line")
		sb.WriteString(strings.Repeat("x", i%7))
		sb.WriteByte('\n')
	}
	body := sb.String()

	if v, _ := compare(t, opt(true), body, body); v != contract.VerdictAC {
		t.Errorf("大输入相同应为 AC，得到 %s", v)
	}
}
