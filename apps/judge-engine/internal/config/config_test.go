package config

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func writeYAML(t *testing.T, body string) string {
	t.Helper()
	p := filepath.Join(t.TempDir(), "config.yaml")
	if err := os.WriteFile(p, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}
	return p
}

func TestDefaultIsValid(t *testing.T) {
	if err := Default().Validate(); err != nil {
		t.Fatalf("默认值自己就不合法: %v", err)
	}
}

// 配置文件缺失不是错误：容器化部署常常只靠环境变量
func TestLoadWithoutFile(t *testing.T) {
	cfg, err := Load("")
	if err != nil {
		t.Fatalf("空路径应当直接用默认值: %v", err)
	}
	if cfg.Judge.HTTPAddr != Default().Judge.HTTPAddr {
		t.Errorf("httpAddr=%q", cfg.Judge.HTTPAddr)
	}

	if _, err := Load(filepath.Join(t.TempDir(), "不存在.yaml")); err != nil {
		t.Errorf("文件不存在应当回退到默认值，got %v", err)
	}
}

// YAML 只写要改的那几项，其余保持默认 —— 这是「不填 ≠ 填 0」的关键
func TestLoadPartialYAMLKeepsDefaults(t *testing.T) {
	p := writeYAML(t, `
judge:
  revealExpected: true
  testdataRoot: /data/testdata
`)
	cfg, err := Load(p)
	if err != nil {
		t.Fatal(err)
	}

	if !cfg.Judge.RevealExpected {
		t.Error("revealExpected 没被 YAML 覆盖")
	}
	if cfg.Judge.TestdataRoot != "/data/testdata" {
		t.Errorf("testdataRoot=%q", cfg.Judge.TestdataRoot)
	}
	// 没写的项必须还是默认值，不能变成零值
	if cfg.Judge.ClockRatio != Default().Judge.ClockRatio {
		t.Errorf("clockRatio 被覆盖成了 %d，没写的项不该动", cfg.Judge.ClockRatio)
	}
	if cfg.Judge.Output.StdoutMaxBytes != Default().Judge.Output.StdoutMaxBytes {
		t.Errorf("stdoutMaxBytes 被覆盖成了 %d", cfg.Judge.Output.StdoutMaxBytes)
	}
}

func TestLoadDuration(t *testing.T) {
	p := writeYAML(t, "judge:\n  sandboxTimeout: 90s\n")
	cfg, err := Load(p)
	if err != nil {
		t.Fatal(err)
	}
	if cfg.Judge.SandboxTimeout.Std() != 90*time.Second {
		t.Errorf("sandboxTimeout=%s want 90s", cfg.Judge.SandboxTimeout)
	}
}

// 拼错的 key 必须报错。静默忽略的话，你会盯着「配置明明写了却不生效」查很久。
func TestLoadRejectsUnknownField(t *testing.T) {
	p := writeYAML(t, "judge:\n  reveaExpected: true\n") // 少了一个 l
	if _, err := Load(p); err == nil {
		t.Fatal("拼错的字段名应当报错，而不是被静默忽略")
	}
}

func TestEnvOverridesYAML(t *testing.T) {
	p := writeYAML(t, "judge:\n  clockRatio: 5\n  revealExpected: false\n")

	t.Setenv("CHERRY_OJ_JUDGE_CLOCK_RATIO", "7")
	t.Setenv("CHERRY_OJ_JUDGE_REVEAL_EXPECTED", "true")
	t.Setenv("CHERRY_OJ_JUDGE_TESTDATA_ROOT", "/srv/from-env")
	t.Setenv("CHERRY_OJ_JUDGE_SANDBOX_TIMEOUT", "5s")
	t.Setenv("CHERRY_OJ_SANDBOX_STORE_MAX_BLOB_BYTES", "123456")
	t.Setenv("CHERRY_OJ_JUDGE_COMPILE_CPU_NS", "999")

	cfg, err := Load(p)
	if err != nil {
		t.Fatal(err)
	}

	if cfg.Judge.ClockRatio != 7 {
		t.Errorf("clockRatio=%d want 7（环境变量应当压过 YAML）", cfg.Judge.ClockRatio)
	}
	if !cfg.Judge.RevealExpected {
		t.Error("revealExpected 没被环境变量覆盖")
	}
	if cfg.Judge.TestdataRoot != "/srv/from-env" {
		t.Errorf("testdataRoot=%q", cfg.Judge.TestdataRoot)
	}
	if cfg.Judge.SandboxTimeout.Std() != 5*time.Second {
		t.Errorf("sandboxTimeout=%s want 5s", cfg.Judge.SandboxTimeout)
	}
	if cfg.Sandbox.Store.MaxBlobBytes != 123456 {
		t.Errorf("maxBlobBytes=%d", cfg.Sandbox.Store.MaxBlobBytes)
	}
	if cfg.Judge.Compile.CPUNs != 999 {
		t.Errorf("compile.cpuNs=%d", cfg.Judge.Compile.CPUNs)
	}
}

// 环境变量写错类型要报错，不能静默当成零值
func TestEnvBadValue(t *testing.T) {
	t.Setenv("CHERRY_OJ_JUDGE_CLOCK_RATIO", "十")
	if _, err := Load(""); err == nil {
		t.Fatal("非整数的 clockRatio 应当报错")
	}

	t.Setenv("CHERRY_OJ_JUDGE_CLOCK_RATIO", "10")
	t.Setenv("CHERRY_OJ_JUDGE_REVEAL_EXPECTED", "是")
	if _, err := Load(""); err == nil {
		t.Fatal("非布尔的 revealExpected 应当报错")
	}
}

func TestValidateCatchesZeroValues(t *testing.T) {
	tests := []struct {
		name  string
		mutIn func(*Config)
	}{
		{"clockRatio 为 0", func(c *Config) { c.Judge.ClockRatio = 0 }},
		{"sandboxTimeout 为 0", func(c *Config) { c.Judge.SandboxTimeout = 0 }},
		{"stdoutMaxBytes 为 0", func(c *Config) { c.Judge.Output.StdoutMaxBytes = 0 }},
		{"maxBlobBytes 为 0", func(c *Config) { c.Sandbox.Store.MaxBlobBytes = 0 }},
		{"testdataRoot 为空", func(c *Config) { c.Judge.TestdataRoot = "" }},
		{"compile 全零", func(c *Config) { c.Judge.Compile = CompileConfig{} }},
		{"parallelism 为负", func(c *Config) { c.Sandbox.Parallelism = -1 }},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cfg := Default()
			tt.mutIn(&cfg)
			if err := cfg.Validate(); err == nil {
				t.Error("应当校验失败")
			}
		})
	}
}

// parallelism=0 是合法的（表示「按 CPU 核数」），别把它和「没配」混为一谈
func TestParallelismZeroIsAllowed(t *testing.T) {
	cfg := Default()
	cfg.Sandbox.Parallelism = 0
	if err := cfg.Validate(); err != nil {
		t.Errorf("parallelism=0 表示按 CPU 核数，应当合法: %v", err)
	}
}

// 默认不泄题 —— 商业部署直接用默认值就是安全的
func TestRevealExpectedDefaultsOff(t *testing.T) {
	if Default().Judge.RevealExpected {
		t.Error("revealExpected 默认必须是 false，教学场景自己打开")
	}
}

func TestCamelToUpperSnake(t *testing.T) {
	tests := []struct{ in, want string }{
		{"testdataRoot", "TESTDATA_ROOT"},
		{"cpuNs", "CPU_NS"},
		{"httpAddr", "HTTP_ADDR"},
		{"maxBlobBytes", "MAX_BLOB_BYTES"},
		{"sandboxURL", "SANDBOX_URL"},
		{"judge", "JUDGE"},
		{"revealExpected", "REVEAL_EXPECTED"},
		{"inlineThresholdBytes", "INLINE_THRESHOLD_BYTES"},
	}
	for _, tt := range tests {
		if got := camelToUpperSnake(tt.in); got != tt.want {
			t.Errorf("camelToUpperSnake(%q)=%q want %q", tt.in, got, tt.want)
		}
	}
}

// 空白严格度是独立于比对算法的一档配置：checker 只管「检测到空白不一致」，
// 判 AC 还是 PE 由这里定。默认必须是宽松的——一个换行不该卡住新手。
func TestStrictWhitespaceDefaultsOff(t *testing.T) {
	if Default().Judge.StrictWhitespace {
		t.Error("strictWhitespace 默认应为 false")
	}
}

func TestStrictWhitespaceFromYAML(t *testing.T) {
	p := writeYAML(t, "judge:\n  strictWhitespace: true\n")
	cfg, err := Load(p)
	if err != nil {
		t.Fatal(err)
	}
	if !cfg.Judge.StrictWhitespace {
		t.Error("YAML 里写了 true 却没生效")
	}
}

func TestStrictWhitespaceFromEnv(t *testing.T) {
	t.Setenv("CHERRY_OJ_JUDGE_STRICT_WHITESPACE", "true")
	cfg, err := Load("")
	if err != nil {
		t.Fatal(err)
	}
	if !cfg.Judge.StrictWhitespace {
		t.Error("环境变量没生效")
	}
}
