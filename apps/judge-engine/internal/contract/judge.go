package contract

import "fmt"

// JudgeMode 决定「测例从哪来」
type JudgeMode string

const (
	// ModeSubmit：正式提交。judge 按 problemId 从测试数据目录读该题全部测例。
	ModeSubmit JudgeMode = "submit"
	// ModeTrial：试运行。用请求里带的 cases——题面样例（server 从库里取出内联发来）
	// 和用户自己敲的输入都走这条。
	ModeTrial JudgeMode = "trial"
)

func (m JudgeMode) IsValid() bool {
	switch m {
	case ModeSubmit, ModeTrial:
		return true
	default:
		return false
	}
}

// UsesProblemTestdata 报告这个模式要不要去测试数据目录读盘
func (m JudgeMode) UsesProblemTestdata() bool { return m == ModeSubmit }

// CaseSpec：一个测试点的输入，以及可选的期望输出。
// expected 缺省 = 只跑不比对（结果是 RAN，不是 AC/WA）。
type CaseSpec struct {
	Input    string `json:"input"`
	Expected string `json:"expected,omitempty"`
	Name     string `json:"name,omitempty"` // 可选：原样回填到 CaseResult.Name
}

// JudgeLimits：本次判题的时空限制。
//
// 真源在 server 的题目表里，由 server 查库后随请求下发——judge 不猜，
// 也不从题目目录里读。这里只放「出题人定的」两项；墙钟倍率、输出上限
// 等属于判题机策略，由 judge 自己决定（见 EffectiveClockNs）。
type JudgeLimits struct {
	CPUNs       int64 `json:"cpuNs"`             // 每个测试点的 CPU 时间上限
	MemoryBytes int64 `json:"memoryBytes"`       // 每个测试点的内存上限
	ClockNs     int64 `json:"clockNs,omitempty"` // 可选：墙钟上限；缺省按 CPUNs 推算
}

// clockRatio：没给 ClockNs 时，墙钟按 CPU 上限的几倍算。
// 程序可能在等 IO（不烧 CPU 但耗墙钟），所以要留出富余；
// 但又不能太大，否则死锁的程序要吊死很久才被杀。
const clockRatio = 10

// EffectiveClockNs 返回实际该用的墙钟上限。
func (l JudgeLimits) EffectiveClockNs() int64 {
	if l.ClockNs > 0 {
		return l.ClockNs
	}
	return l.CPUNs * clockRatio
}

// Validate 检查限制是否可用。
// 零值必须挡在入口：CPUNs=0 会让每个测试点秒 TLE，MemoryBytes=0 会秒 MLE
func (l JudgeLimits) Validate() error {
	if l.CPUNs <= 0 {
		return fmt.Errorf("limits.cpuNs 必须为正，得到 %d", l.CPUNs)
	}
	if l.MemoryBytes <= 0 {
		return fmt.Errorf("limits.memoryBytes 必须为正，得到 %d", l.MemoryBytes)
	}
	if l.ClockNs < 0 {
		return fmt.Errorf("limits.clockNs 不能为负，得到 %d", l.ClockNs)
	}
	return nil
}

// JudgeRequest：判题请求
type JudgeRequest struct {
	SubmissionID string      `json:"submissionId"`    // 只用于标识/对账，不参与判题逻辑
	ProblemID    string      `json:"problemId"`       // 只用于加载测例文件
	Language     string      `json:"language"`        // 决定编译/运行怎么编排
	Source       string      `json:"source"`          // 源码全文
	Limits       JudgeLimits `json:"limits"`          // 时空限制
	Mode         JudgeMode   `json:"mode,omitempty"`  // 缺省 official
	Cases        []CaseSpec  `json:"cases,omitempty"` // 仅 mode=trial
}

// Output：用户程序的输出，非 AC 时带回供排查。
//
// 注意 sandbox 侧已按 StdoutMaxBytes 截过一次，所以 Bytes 是 judge 实际
// 收到的字节数，不一定等于程序真实产出的长度。
type Output struct {
	Excerpt   string `json:"excerpt"`             // 前若干字节（判题机策略）
	Bytes     int64  `json:"bytes"`               // judge 收到的总字节数
	Truncated bool   `json:"truncated,omitempty"` // Excerpt 短于 Bytes
	Ref       string `json:"ref,omitempty"`       // 可选：完整内容的引用，MVP 可不填
}

// Diff：WA 时第一处不同的位置。
//
// ★ Want 填不填由判题机的运行配置决定（judge.revealExpected）：
// 商业 OJ 关掉，避免用户刷 WA 把题库答案拖走；教学场景打开，
// 让学生看到自己的输出和答案差在哪。关掉时只给 Line 和 Got。
type Diff struct {
	Line int    `json:"line"`           // 第一处不同的行号，从 1 起
	Got  string `json:"got,omitempty"`  // 该行用户输出；空 = 用户少输出了这一行
	Want string `json:"want,omitempty"` // 该行标准答案；revealExpected=false 时留空
}

// CaseResult：单个测试点的判题结果
type CaseResult struct {
	Idx     int     `json:"idx"`            // 本次判题中的序号，从 1 开始
	Name    string  `json:"name,omitempty"` // 测试点名：submit 取自文件名，trial 取自 CaseSpec.Name
	Verdict Verdict `json:"verdict"`
	Time    int64   `json:"time,omitempty"`   // ns
	Memory  int64   `json:"memory,omitempty"` // bytes
	Message string  `json:"message,omitempty"`
	Output  *Output `json:"output,omitempty"` // 非 AC 时带回
	Diff    *Diff   `json:"diff,omitempty"`   // 仅 WA 时
}

// JudgeResult：判题结果
type JudgeResult struct {
	Verdict Verdict `json:"verdict"`
	Time    int64   `json:"time,omitempty"`   // 各点最大值，ns
	Memory  int64   `json:"memory,omitempty"` // 各点最大值，bytes
	Score   int     `json:"score"`
	Message string  `json:"message,omitempty"` // 如 CE 的编译器输出
	// Cases 的顺序即实际执行顺序，Idx 从 1 递增。
	// trial 模式下严格对应请求里的 Cases[i]——调用方靠这个把结果对回去，judge 不得重排。
	Cases []CaseResult `json:"cases,omitempty"`
}
