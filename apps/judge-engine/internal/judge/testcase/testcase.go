package testcase

import (
	"cherry-oj/judge-engine/internal/contract"
	"fmt"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
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

// Options 是 Load 的可选参数。
//
// 用结构体而不是裸参数，和 api.Options 保持一致：调用处
// testcase.Load(root, id, testcase.Options{Logger: lg}) 一眼看懂在传什么。
type Options struct {
	// Logger：缺少 .out 之类的数据问题往这里报。nil = slog.Default()。
	//
	// 不直接用包级 log.Printf，是因为那样调用方接管不了——测试里没法断言
	// 「确实警告了」，线上也没法把它并进结构化日志。
	Logger *slog.Logger
}

func (o Options) logger() *slog.Logger {
	if o.Logger != nil {
		return o.Logger
	}
	return slog.Default()
}

func Load(testdataRoot, problemID string, opts Options) ([]TestCase, error) {
	log := opts.logger()

	if !idPattern.MatchString(problemID) {
		return nil, fmt.Errorf("非法 problemId: %q", problemID)
	}

	dir := filepath.Join(testdataRoot, problemID)
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, fmt.Errorf("读测试数据目录 %q: %w", dir, err)
	}

	var cases []TestCase
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".in") {
			continue
		}
		name := strings.TrimSuffix(e.Name(), ".in")
		inPath := filepath.Join(dir, e.Name())
		outPath := filepath.Join(dir, name+".out")

		inInfo, err := os.Stat(inPath)
		if err != nil {
			return nil, fmt.Errorf("stat %q: %w", inPath, err)
		}
		outInfo, err := os.Stat(outPath)
		if err != nil {
			if os.IsNotExist(err) {
				// 出题人少传一个文件是常见事故：跳过这个点，但必须留痕。
				log.Warn("测试点缺少对应的 .out，已跳过",
					"problemID", problemID, "case", name, "expect", outPath)
				continue
			}
			return nil, fmt.Errorf("stat %q: %w", outPath, err)
		}

		cases = append(cases, TestCase{
			Name: name,
			Input: Blob{
				Size: inInfo.Size(),
				Open: func() (io.ReadCloser, error) { return os.Open(inPath) },
			},
			Expected: &Blob{
				Size: outInfo.Size(),
				Open: func() (io.ReadCloser, error) { return os.Open(outPath) },
			},
		})
	}

	if len(cases) == 0 {
		return nil, fmt.Errorf("题目 %q 没有配对的测试点", problemID)
	}
	sort.Slice(cases, func(i, j int) bool {
		return lessName(cases[i].Name, cases[j].Name)
	})
	return cases, nil
}

// FromSpecs 把请求里内联的测例转成同样的 TestCase，供 trial 模式用。
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

// lessName：能解析成整数的名字按数值排，并整体排在非数值名字前面；
// 非数值之间按字符串排。这样 1,2,10 不会变成 1,10,2。
func lessName(a, b string) bool {
	ai, aErr := strconv.Atoi(a)
	bi, bErr := strconv.Atoi(b)
	aNum, bNum := aErr == nil, bErr == nil
	switch {
	case aNum && bNum:
		return ai < bi
	case aNum:
		return true
	case bNum:
		return false
	default:
		return a < b
	}
}

var idPattern = regexp.MustCompile(`^[a-z0-9][a-z0-9-]{0,63}$`)
