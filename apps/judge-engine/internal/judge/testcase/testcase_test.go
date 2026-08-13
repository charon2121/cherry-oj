package testcase_test

import (
	"bytes"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/judge/testcase"
)

const root = "testdata"

// read 打开一个 Blob 并读完，失败直接 Fatal。
func read(t *testing.T, b testcase.Blob) string {
	t.Helper()
	rc, err := b.Open()
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer rc.Close()
	got, err := io.ReadAll(rc)
	if err != nil {
		t.Fatalf("ReadAll: %v", err)
	}
	return string(got)
}

// names 把测试点名按顺序取出来，方便断言排序。
func names(cases []testcase.TestCase) []string {
	out := make([]string, len(cases))
	for i, c := range cases {
		out[i] = c.Name
	}
	return out
}

func TestLoad(t *testing.T) {
	cases, err := testcase.Load(root, "a-plus-b", testcase.Options{})
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if len(cases) != 3 {
		t.Fatalf("测试点数 = %d, want 3 (%v)", len(cases), names(cases))
	}

	// 内容对得上，且和 Name 是同一个测试点的
	want := []struct{ name, in, out string }{
		{"1", "1 2\n", "3\n"},
		{"2", "-5 8\n", "3\n"},
		{"10", "100 200\n", "300\n"},
	}
	for i, w := range want {
		c := cases[i]
		if c.Name != w.name {
			t.Errorf("cases[%d].Name = %q, want %q", i, c.Name, w.name)
		}
		if got := read(t, c.Input); got != w.in {
			t.Errorf("cases[%d] input = %q, want %q", i, got, w.in)
		}
		if c.Expected == nil {
			t.Fatalf("cases[%d].Expected 不该为 nil", i)
		}
		if got := read(t, *c.Expected); got != w.out {
			t.Errorf("cases[%d] expected = %q, want %q", i, got, w.out)
		}
	}
}

// ★ 数值排序：目录里是 1/2/10，字符串排序会给出 1,10,2。
// 顺序错了，「第几个点挂了」就是错的，用户照着去查只会更迷惑。
func TestLoadSortsNumerically(t *testing.T) {
	cases, err := testcase.Load(root, "a-plus-b", testcase.Options{})
	if err != nil {
		t.Fatal(err)
	}
	want := []string{"1", "2", "10"}
	got := names(cases)
	if len(got) != len(want) {
		t.Fatalf("got %v, want %v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("顺序 = %v, want %v（字符串排序会给出 1,10,2）", got, want)
		}
	}
}

// 数值名排在非数值名前面；非数值之间按字符串排
func TestLoadMixedNames(t *testing.T) {
	cases, err := testcase.Load(root, "mixed-names", testcase.Options{})
	if err != nil {
		t.Fatal(err)
	}
	want := []string{"1", "2", "big-1", "small"}
	got := names(cases)
	if strings.Join(got, ",") != strings.Join(want, ",") {
		t.Errorf("顺序 = %v, want %v", got, want)
	}
}

// Size 必须和文件真实大小一致 —— flow 靠它决定内联还是走 store ref
func TestLoadBlobSize(t *testing.T) {
	cases, err := testcase.Load(root, "a-plus-b", testcase.Options{})
	if err != nil {
		t.Fatal(err)
	}
	for _, c := range cases {
		inPath := filepath.Join(root, "a-plus-b", c.Name+".in")
		fi, err := os.Stat(inPath)
		if err != nil {
			t.Fatal(err)
		}
		if c.Input.Size != fi.Size() {
			t.Errorf("%s: Input.Size = %d, want %d", c.Name, c.Input.Size, fi.Size())
		}
		if int64(len(read(t, c.Input))) != c.Input.Size {
			t.Errorf("%s: Size 和实际读到的字节数对不上", c.Name)
		}
	}
}

// ★ 这条锁住的是 testcase 包存在的理由：Load 只记「怎么打开」，不读内容。
// 若哪天被改成加载时就读进内存，上面那些用例照样全绿，只有这条会响。
func TestLoadDoesNotReadContents(t *testing.T) {
	// 在临时目录里造一份，这样可以放心删文件
	dir := filepath.Join(t.TempDir(), "probe")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	for name, body := range map[string]string{"1.in": "1 2\n", "1.out": "3\n"} {
		if err := os.WriteFile(filepath.Join(dir, name), []byte(body), 0o600); err != nil {
			t.Fatal(err)
		}
	}

	cases, err := testcase.Load(filepath.Dir(dir), "probe", testcase.Options{})
	if err != nil {
		t.Fatal(err)
	}
	if len(cases) != 1 {
		t.Fatalf("测试点数 = %d", len(cases))
	}

	// Load 之后把文件删掉：如果内容已经在内存里，Open 就还能成功
	if err := os.Remove(filepath.Join(dir, "1.in")); err != nil {
		t.Fatal(err)
	}
	if rc, err := cases[0].Input.Open(); err == nil {
		rc.Close()
		t.Error("文件已删除，Open 却成功了 —— Load 把内容读进内存了")
	}
}

// 落单的 .in 跳过，不影响其它测试点；落单的 .out 直接无视
func TestLoadSkipsUnpaired(t *testing.T) {
	cases, err := testcase.Load(root, "unpaired", testcase.Options{})
	if err != nil {
		t.Fatalf("有一个落单的 .in 不该让整次加载失败: %v", err)
	}
	if len(cases) != 1 || cases[0].Name != "1" {
		t.Fatalf("got %v, want [1]", names(cases))
	}
}

// ★ 跳过必须留痕。静默跳过的话，出题人少传一个 .out 就会变成
// 「这题只有 9 个测试点」，而没有任何人知道——错解可能因此拿到 AC。
func TestLoadWarnsOnUnpaired(t *testing.T) {
	var buf bytes.Buffer
	logger := slog.New(slog.NewTextHandler(&buf, &slog.HandlerOptions{Level: slog.LevelWarn}))

	if _, err := testcase.Load(root, "unpaired", testcase.Options{Logger: logger}); err != nil {
		t.Fatal(err)
	}

	got := buf.String()
	if got == "" {
		t.Fatal("跳过测试点时没有发出任何警告")
	}
	// 警告里要能定位到是哪道题的哪个点，否则运维看到也不知道去查什么
	for _, want := range []string{"unpaired", "2"} {
		if !strings.Contains(got, want) {
			t.Errorf("警告里缺少 %q，无法定位: %s", want, got)
		}
	}
}

// Logger 为 nil 时走 slog.Default()，不能 panic
func TestLoadNilLoggerIsSafe(t *testing.T) {
	if _, err := testcase.Load(root, "unpaired", testcase.Options{Logger: nil}); err != nil {
		t.Fatal(err)
	}
}

// 一个都配不上 → 报错，别静默返回空切片让上层以为「这题就是没测试点」
func TestLoadNoPairsIsError(t *testing.T) {
	if _, err := testcase.Load(root, "no-pairs", testcase.Options{}); err == nil {
		t.Error("目录里没有任何配对的 .in/.out，应当报错")
	}
}

func TestLoadEmptyDirIsError(t *testing.T) {
	dir := filepath.Join(t.TempDir(), "empty")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	if _, err := testcase.Load(filepath.Dir(dir), "empty", testcase.Options{}); err == nil {
		t.Error("空目录应当报错")
	}
}

func TestLoadMissingDirIsError(t *testing.T) {
	if _, err := testcase.Load(root, "no-such-problem", testcase.Options{}); err == nil {
		t.Error("题目目录不存在应当报错")
	}
}

// ★ problemId 来自 HTTP 请求，绝不能直接拼进路径。
// 只断言 Load 报错，不去真造一个 ../ 目录 —— 测防护不该以触发它为代价。
func TestLoadRejectsBadID(t *testing.T) {
	bad := []string{
		"",
		"../a-plus-b",
		"../../etc",
		"/etc",
		"a/b",
		"A-PLUS-B", // 大写不合法
		"a_plus_b", // 下划线不合法
		"-leading", // 不能以连字符开头
		".",
		"..",
		strings.Repeat("x", 65), // 超长
	}
	for _, id := range bad {
		t.Run(id, func(t *testing.T) {
			if _, err := testcase.Load(root, id, testcase.Options{}); err == nil {
				t.Errorf("Load(%q) 应当报错", id)
			}
		})
	}
}

func TestFromSpecs(t *testing.T) {
	specs := []contract.CaseSpec{
		{Input: "1 2\n", Expected: "3\n", Name: "样例1"},
		{Input: "-5 8\n", Expected: "3\n", Name: "样例2"},
	}
	cases := testcase.FromSpecs(specs)
	if len(cases) != 2 {
		t.Fatalf("测试点数 = %d, want 2", len(cases))
	}

	for i, spec := range specs {
		c := cases[i]
		if c.Name != spec.Name {
			t.Errorf("cases[%d].Name = %q, want %q", i, c.Name, spec.Name)
		}
		if got := read(t, c.Input); got != spec.Input {
			t.Errorf("cases[%d] input = %q, want %q", i, got, spec.Input)
		}
		if c.Input.Size != int64(len(spec.Input)) {
			t.Errorf("cases[%d].Input.Size = %d, want %d", i, c.Input.Size, len(spec.Input))
		}
		if c.Expected == nil {
			t.Fatalf("cases[%d].Expected 不该为 nil", i)
		}
		if got := read(t, *c.Expected); got != spec.Expected {
			t.Errorf("cases[%d] expected = %q, want %q", i, got, spec.Expected)
		}
	}
}

// ★ 每个测试点的闭包必须捕获自己那一份数据。
// 循环变量捕获写错的话，所有测试点都会读到最后一条 —— 而且「能跑通」，
// 只是每个点判的都是同一份输入，结论看起来还挺合理。
func TestFromSpecsClosuresAreIndependent(t *testing.T) {
	cases := testcase.FromSpecs([]contract.CaseSpec{
		{Input: "first\n", Expected: "1\n"},
		{Input: "second\n", Expected: "2\n"},
		{Input: "third\n", Expected: "3\n"},
	})
	// 倒着读，避免「碰巧按顺序读才对」
	for i := len(cases) - 1; i >= 0; i-- {
		want := []string{"first\n", "second\n", "third\n"}[i]
		if got := read(t, cases[i].Input); got != want {
			t.Errorf("cases[%d] input = %q, want %q", i, got, want)
		}
	}
}

// Blob 可以被打开多次 —— flow 里重试或先探大小再读都需要这一点
func TestBlobIsReopenable(t *testing.T) {
	cases, err := testcase.Load(root, "a-plus-b", testcase.Options{})
	if err != nil {
		t.Fatal(err)
	}
	first := read(t, cases[0].Input)
	second := read(t, cases[0].Input)
	if first != second {
		t.Errorf("两次 Open 读到的内容不同: %q vs %q", first, second)
	}
}

// expected 缺省 = 只跑不比对（RAN），Expected 必须是 nil。
// 契约里 CaseSpec.expected 是 omitempty，「缺省则该点不跑 checker」。
func TestFromSpecsEmptyExpectedIsNil(t *testing.T) {
	cases := testcase.FromSpecs([]contract.CaseSpec{
		{Input: "1 2\n"}, // 用户只想看看程序输出什么，没给答案
	})
	if len(cases) != 1 {
		t.Fatalf("测试点数 = %d", len(cases))
	}
	if cases[0].Expected != nil {
		t.Errorf("expected 缺省时 Expected 应为 nil（表示只跑不比对），"+
			"得到一个 Size=%d 的 Blob —— 会被当成「答案是空字符串」，"+
			"于是有输出的程序全判 WA", cases[0].Expected.Size)
	}
}

func TestFromSpecsEmptyInput(t *testing.T) {
	cases := testcase.FromSpecs(nil)
	if len(cases) != 0 {
		t.Errorf("got %d cases, want 0", len(cases))
	}
}
