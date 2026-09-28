package testcase

import (
	"cherry-oj/judge-engine/internal/contract"
	"fmt"
	"io"
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

// Load 读取一个测试数据版本目录下成对的 name.in / name.out，按名字排序返回。
//
// 只要有一个 .in 缺 .out，或一个 .out 缺 .in，就整体报错，不跳过：少判一个点会让错解
// 拿到 AC，而错误在结论里完全看不出来。节点安装测试数据时已经校验过成对，这里报错
// 意味着目录被手工改动过，应当由人检查。
func Load(testdataRoot, testDataVersionID string) ([]TestCase, error) {
	if !idPattern.MatchString(testDataVersionID) {
		return nil, fmt.Errorf("illegal testDataVersionId: %q", testDataVersionID)
	}

	dir := filepath.Join(testdataRoot, testDataVersionID)
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, fmt.Errorf("read test data directory %q: %w", dir, err)
	}

	present := map[string]bool{}
	for _, e := range entries {
		if !e.IsDir() {
			present[e.Name()] = true
		}
	}
	var cases []TestCase
	var missing []string
	for name := range present {
		base, isIn := strings.CutSuffix(name, ".in")
		if !isIn {
			if base, isOut := strings.CutSuffix(name, ".out"); isOut && !present[base+".in"] {
				missing = append(missing, base+".in")
			}
			continue
		}
		if !present[base+".out"] {
			missing = append(missing, base+".out")
			continue
		}
		c, err := pair(dir, base)
		if err != nil {
			return nil, err
		}
		cases = append(cases, c)
	}
	if len(missing) > 0 {
		sort.Strings(missing)
		return nil, fmt.Errorf("test data version %q is missing %s", testDataVersionID, strings.Join(missing, ", "))
	}
	if len(cases) == 0 {
		return nil, fmt.Errorf("test data version %q has no paired test cases", testDataVersionID)
	}
	sort.Slice(cases, func(i, j int) bool {
		return lessName(cases[i].Name, cases[j].Name)
	})
	return cases, nil
}

// pair 只记录两个文件的大小和打开方式，不读内容：几十 MB 的测例乘以并发数会很快耗尽内存。
func pair(dir, name string) (TestCase, error) {
	inPath := filepath.Join(dir, name+".in")
	outPath := filepath.Join(dir, name+".out")
	inInfo, err := os.Stat(inPath)
	if err != nil {
		return TestCase{}, fmt.Errorf("stat %q: %w", inPath, err)
	}
	outInfo, err := os.Stat(outPath)
	if err != nil {
		return TestCase{}, fmt.Errorf("stat %q: %w", outPath, err)
	}
	return TestCase{
		Name:     name,
		Input:    Blob{Size: inInfo.Size(), Open: func() (io.ReadCloser, error) { return os.Open(inPath) }},
		Expected: &Blob{Size: outInfo.Size(), Open: func() (io.ReadCloser, error) { return os.Open(outPath) }},
	}, nil
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
