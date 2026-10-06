package testcase_test

import (
	"io"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/testcase"
)

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
