// Package config 定义 judge 服务的运行配置。
//
// 它只描述 judge 自己需要的东西：sandbox 段配错不会让 judge 拒绝启动，反之亦然。
// 装配机制（默认值 → YAML → 环境变量 → 校验）在 internal/platform/config。
package config

import platform "cherry-oj/judge-engine/internal/platform/config"

// Load 按「默认值 → YAML → 环境变量」装配 judge 配置并校验。
func Load(path string) (Config, error) { return platform.Load(path, Default()) }

// Config 是 judge 服务的全部运行配置。
//
// 两个小节对应环境变量前缀 `CHERRY_OJ_LOGGING_*` 与 `CHERRY_OJ_JUDGE_*`。
// **YAML 键名即部署契约**：compose 与部署清单按这些名字注入环境变量，改名等于改部署。
type Config struct {
	Logging platform.Logging `yaml:"logging"`
	Judge   Settings         `yaml:"judge"`
}

// Settings 是 judge 段的运行策略。
type Settings struct {
	Node     Node   `yaml:"node"`
	HTTPAddr string `yaml:"httpAddr"`
	// SandboxURL/SandboxTimeout：judge 通过 HTTP 使用 sandbox，超时必须覆盖对端最慢的一次操作，
	// 设小了会出现「沙箱正常跑着，judge 自己先超时」，报出来是 SE，查半天查不到原因。
	SandboxURL     string            `yaml:"sandboxURL"`
	SandboxTimeout platform.Duration `yaml:"sandboxTimeout"`
	// EnvironmentFingerprint：实际部署环境的不可变标识，随每个 JudgeResult 返回。
	// judging-service 用它核对任务是否被路由到了 JudgeInput 冻结的环境。
	// 启用节点链路时由注册身份覆盖，见 node.Identity。
	EnvironmentFingerprint string `yaml:"environmentFingerprint"`
	// TestdataRoot：测试数据根目录，下面按 testDataVersionId 分子目录。
	TestdataRoot string `yaml:"testdataRoot"`

	// StrictWhitespace：token 全对、但空白排布和标准答案不一致时，判 PE 还是 AC。
	//
	// 比对器一律**检测**空白差异——行首缩进、行内空格数量、行尾空白、
	// 换行位置、缺末尾换行、\r\n，全都算。这里只决定拿这个事实怎么定性：
	//
	//   true  —— 严格，判 PE。对输出格式有要求的比赛 / 想让用户养成规范的教学场景。
	//   false —— 宽松，判 AC。选手 cout << x 不写 endl 也能过。
	//
	// 注意空白差异**永远不会**变成 WA：答案的内容是对的，只是排版不同。
	// 判成 WA 会让选手去查一个根本不存在的算法错误。
	StrictWhitespace bool `yaml:"strictWhitespace"`

	// RevealExpected：WA 时要不要把标准答案（Diff.Want）回给调用方。
	//
	// 商业 OJ 关掉——否则用户刷几次 WA 就能把整套题库答案拖走。
	// 教学场景打开——让用户看清自己的输出和答案差在哪，这正是判题的教学价值。
	// 关掉时仍然回传行号和用户自己的那一行，够定位，不泄题。
	RevealExpected bool `yaml:"revealExpected"`

	// ClockRatio：请求没指定墙钟上限时，墙钟 = cpuNs × 本值。
	// 程序可能在等 IO（不烧 CPU 但耗墙钟），要留富余；太大则死锁的程序要吊很久才被杀。
	ClockRatio int64 `yaml:"clockRatio"`

	// InlineThresholdBytes：测例输入超过这个大小就先传进 sandbox 的 store 走 ref，
	// 否则内联进 /run 的 JSON。小数据内联省一次往返，大数据走 ref 省内存。
	InlineThresholdBytes int64 `yaml:"inlineThresholdBytes"`

	// OutputExcerptBytes：非 AC 时回传多少字节的用户输出。
	OutputExcerptBytes int `yaml:"outputExcerptBytes"`
	// MessageExcerptBytes：CE 的编译器输出 / RE 的 stderr 截多长。
	// g++ 一个模板错误能吐几十万字，原样存进数据库很快就会后悔。
	MessageExcerptBytes int `yaml:"messageExcerptBytes"`

	// Output：让 sandbox 最多替程序收多少输出（judge 填进 RunSpec.Limits）。
	//
	// 放在 judge 段而不是 sandbox 段，是因为这两个值是 judge 组 RunSpec 时填进
	// Limits 里发给 sandbox 的——sandbox 只是照办。它们属于判题机策略（不是出题人
	// 能定的东西），所以在配置里而不在 JudgeRequest 里。
	Output Output `yaml:"output"`

	// Compile：编译那一步的资源上限。它和题目的时空限制无关——
	// 出题人管的是「跑得多快算超时」，编译该给多少资源是判题机的事。
	Compile Compile `yaml:"compile"`
}

type Output struct {
	StdoutMaxBytes int64 `yaml:"stdoutMaxBytes"`
	StderrMaxBytes int64 `yaml:"stderrMaxBytes"`
}

type Compile struct {
	CPUNs       int64 `yaml:"cpuNs"`
	MemoryBytes int64 `yaml:"memoryBytes"`
	ClockNs     int64 `yaml:"clockNs"`
}
