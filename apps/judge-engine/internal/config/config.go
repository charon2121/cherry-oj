// Package config 加载判题引擎的运行配置。
//
// 三层来源，后面的覆盖前面的：
//
//	默认值（Default）→ YAML 文件 → 环境变量（CHERRY_OJ_*）
//
// 什么该放这里：**运行策略**——比对方式、要不要回传标准答案、各类大小上限、
// 监听地址。它们的共同点是「换个部署环境就可能要改，但和某一次判题请求无关」。
//
// 什么不该放这里：随请求变的东西（题目的时空限制、源码、测例）——那些走
// contract 里的 JudgeRequest。判断标准：**进程生命周期内不变的进配置，
// 每次请求都可能不同的进请求。**
package config

import (
	"fmt"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

// Duration 让配置文件里能写 "60s" 而不是 60000000000。
//
// 契约（judge.schema.json）里坚持用 ns，是因为那是给机器和跨语言调用方看的；
// 配置文件是给人编辑的，可读性优先。两边受众不同，规则可以不同。
type Duration time.Duration

func (d Duration) Std() time.Duration { return time.Duration(d) }
func (d Duration) String() string     { return time.Duration(d).String() }

func (d *Duration) UnmarshalYAML(value *yaml.Node) error {
	var s string
	if err := value.Decode(&s); err != nil {
		return fmt.Errorf("时长应当写成字符串如 \"60s\": %w", err)
	}
	parsed, err := time.ParseDuration(s)
	if err != nil {
		return fmt.Errorf("解析时长 %q: %w", s, err)
	}
	*d = Duration(parsed)
	return nil
}

type Config struct {
	Sandbox SandboxConfig `yaml:"sandbox"`
	Judge   JudgeConfig   `yaml:"judge"`
}

type SandboxConfig struct {
	HTTPAddr string `yaml:"httpAddr"`
	// Parallelism：最多同时跑几个程序。0 = 按 CPU 核数。
	Parallelism int         `yaml:"parallelism"`
	Store       StoreConfig `yaml:"store"`
}

type StoreConfig struct {
	// Root：blob 落盘的根目录。空 = 自动探测 /dev/shm，不可用则回退到系统临时目录。
	Root string `yaml:"root"`
	// MaxBlobBytes：单次上传的上限。/dev/shm 是内存盘，没有上限一次大上传就能撑爆 RAM。
	MaxBlobBytes int64 `yaml:"maxBlobBytes"`
}

// OutputConfig：最多替被判的程序收多少输出。
//
// 放在 judge 段而不是 sandbox 段，是因为这两个值是 judge 组 RunSpec 时填进
// Limits 里发给 sandbox 的——sandbox 只是照办。它们属于判题机策略（不是出题人
// 能定的东西），所以在配置里而不在 JudgeRequest 里。
type OutputConfig struct {
	StdoutMaxBytes int64 `yaml:"stdoutMaxBytes"`
	StderrMaxBytes int64 `yaml:"stderrMaxBytes"`
}

type JudgeConfig struct {
	HTTPAddr       string   `yaml:"httpAddr"`
	SandboxURL     string   `yaml:"sandboxURL"`
	SandboxTimeout Duration `yaml:"sandboxTimeout"`
	// TestdataRoot：测试数据根目录，下面按 problemId 分子目录。
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
	Output OutputConfig `yaml:"output"`

	// Compile：编译那一步的资源上限。它和题目的时空限制无关——
	// 出题人管的是「跑得多快算超时」，编译该给多少资源是判题机的事。
	Compile CompileConfig `yaml:"compile"`
}

type CompileConfig struct {
	CPUNs       int64 `yaml:"cpuNs"`
	MemoryBytes int64 `yaml:"memoryBytes"`
	ClockNs     int64 `yaml:"clockNs"`
}

// Default 返回一套能直接跑起来的默认值。
//
// 有默认值意味着**配置文件可以缺失**，也意味着 YAML 里只需要写要改的那几项——
// 这是「零值陷阱」的解药：不填不等于填 0。
func Default() Config {
	return Config{
		Sandbox: SandboxConfig{
			HTTPAddr:    "127.0.0.1:5050",
			Parallelism: 0, // 0 = NumCPU，由 pool.New 兜底
			Store: StoreConfig{
				Root:         "", // 空 = 自动探测
				MaxBlobBytes: 64 << 20,
			},
		},
		Judge: JudgeConfig{
			HTTPAddr:             "127.0.0.1:5051",
			SandboxURL:           "http://127.0.0.1:5050",
			SandboxTimeout:       Duration(60 * time.Second),
			TestdataRoot:         "/srv/cherry-oj/testdata",
			StrictWhitespace:     false, // 默认宽松：一个换行不该卡住新手
			RevealExpected:       false, // ★ 默认不泄题；教学部署自己打开
			ClockRatio:           10,
			InlineThresholdBytes: 256 << 10,
			OutputExcerptBytes:   4 << 10,
			MessageExcerptBytes:  8 << 10,
			Output: OutputConfig{
				StdoutMaxBytes: 64 << 20,
				StderrMaxBytes: 16 << 20,
			},
			Compile: CompileConfig{
				CPUNs:       10_000_000_000, // 10s
				MemoryBytes: 1 << 30,        // 1 GiB
				ClockNs:     20_000_000_000, // 20s
			},
		},
	}
}

// Load 按「默认值 → YAML 文件 → 环境变量」的顺序装配配置。
//
// path 为空，或文件不存在，都只用默认值 + 环境变量——**这不是错误**。
// 容器化部署里全靠环境变量、根本不挂配置文件，是很常见的做法。
func Load(path string) (Config, error) {
	cfg := Default()

	if path != "" {
		b, err := os.ReadFile(path)
		switch {
		case err == nil:
			// KnownFields：YAML 里出现结构体没有的字段就报错。
			// 少了这行，一个拼错的 key（reveaExpected）会被静默忽略，
			// 你会盯着「配置明明开了却不生效」查很久。
			dec := yaml.NewDecoder(bytesReader(b))
			dec.KnownFields(true)
			if err := dec.Decode(&cfg); err != nil {
				return Config{}, fmt.Errorf("解析配置文件 %s: %w", path, err)
			}
		case os.IsNotExist(err):
			// 不存在就算了，用默认值 + 环境变量
		default:
			return Config{}, fmt.Errorf("读取配置文件 %s: %w", path, err)
		}
	}

	if err := applyEnv(&cfg); err != nil {
		return Config{}, err
	}
	if err := cfg.Validate(); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

// Validate 把「配错了」挡在启动时，而不是让它伪装成一个合理的判题结论。
//
// 这条教训在这个项目里反复出现：limits 的 cpuNs=0 会让每道题秒 TLE，
// 而返回的结果看起来完全正常。配置也一样——宁可起不来，也别悄悄跑错。
func (c Config) Validate() error {
	if c.Sandbox.HTTPAddr == "" {
		return fmt.Errorf("sandbox.httpAddr 不能为空")
	}
	if c.Sandbox.Parallelism < 0 {
		return fmt.Errorf("sandbox.parallelism 不能为负，得到 %d", c.Sandbox.Parallelism)
	}
	if c.Sandbox.Store.MaxBlobBytes <= 0 {
		return fmt.Errorf("sandbox.store.maxBlobBytes 必须为正，得到 %d", c.Sandbox.Store.MaxBlobBytes)
	}

	j := c.Judge
	if j.HTTPAddr == "" {
		return fmt.Errorf("judge.httpAddr 不能为空")
	}
	if j.SandboxURL == "" {
		return fmt.Errorf("judge.sandboxURL 不能为空")
	}
	if j.SandboxTimeout <= 0 {
		return fmt.Errorf("judge.sandboxTimeout 必须为正，得到 %s", j.SandboxTimeout)
	}
	if j.TestdataRoot == "" {
		return fmt.Errorf("judge.testdataRoot 不能为空")
	}
	if j.ClockRatio <= 0 {
		return fmt.Errorf("judge.clockRatio 必须为正，得到 %d", j.ClockRatio)
	}
	if j.InlineThresholdBytes < 0 {
		return fmt.Errorf("judge.inlineThresholdBytes 不能为负，得到 %d", j.InlineThresholdBytes)
	}
	if j.OutputExcerptBytes < 0 {
		return fmt.Errorf("judge.outputExcerptBytes 不能为负，得到 %d", j.OutputExcerptBytes)
	}
	if j.MessageExcerptBytes < 0 {
		return fmt.Errorf("judge.messageExcerptBytes 不能为负，得到 %d", j.MessageExcerptBytes)
	}
	if j.Output.StdoutMaxBytes <= 0 {
		return fmt.Errorf("judge.output.stdoutMaxBytes 必须为正，得到 %d", j.Output.StdoutMaxBytes)
	}
	if j.Output.StderrMaxBytes <= 0 {
		return fmt.Errorf("judge.output.stderrMaxBytes 必须为正，得到 %d", j.Output.StderrMaxBytes)
	}
	if j.Compile.CPUNs <= 0 || j.Compile.MemoryBytes <= 0 || j.Compile.ClockNs <= 0 {
		return fmt.Errorf("judge.compile 的三项都必须为正，得到 %+v", j.Compile)
	}
	return nil
}
