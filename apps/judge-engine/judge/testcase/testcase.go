package testcase

import (
	"cherry-oj/judge-engine/internal/contract"
	"io"
	"strings"
)

type Blob struct {
	Size int64                         // 文件大小（字节），0 = 未知
	Open func() (io.ReadCloser, error) // 打开文件
}

type TestCase struct {
	Name     string // "1" / "big-3"，submit 模式取自文件名；trial 模式留空
	Input    Blob
	Expected *Blob // nil = 只跑不比对（结果是 RAN）
}

// FromSpecs 把请求里内联的测试点转成同样的 TestCase，供 trial 模式用。
//
// 不返回 error：这里只是把内存里的字符串包一层，没有任何会失败的动作。
// 硬加一个恒为 nil 的 error，只会让每个调用点白写一次 if err != nil。
func FromSpecs(specs []contract.CaseSpec) []TestCase {
	cases := make([]TestCase, 0, len(specs))
	for _, spec := range specs {
		c := TestCase{
			Name: spec.Name,
			Input: Blob{
				Size: int64(len(spec.Input)),
				Open: func() (io.ReadCloser, error) { return io.NopCloser(strings.NewReader(spec.Input)), nil },
			},
		}
		// ★ expected 缺省 = 只跑不比对（RAN）。
		// 无条件构造 Blob 的话，空答案会被当成「标准答案是空字符串」，
		// 于是有输出的程序全判 WA——而且只在 trial 模式触发，很难发现。
		if spec.Expected != "" {
			c.Expected = &Blob{
				Size: int64(len(spec.Expected)),
				Open: func() (io.ReadCloser, error) { return io.NopCloser(strings.NewReader(spec.Expected)), nil },
			}
		}
		cases = append(cases, c)
	}
	return cases
}
