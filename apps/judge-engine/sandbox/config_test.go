package sandbox

import (
	"os"
	"path/filepath"
	"testing"

	"cherry-oj/judge-engine/sandbox/internal/backend"
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
	if err := DefaultConfig().Validate(); err != nil {
		t.Fatalf("默认值自己就不合法: %v", err)
	}
}

// 缺省已有正数默认值，显式 0 拒绝启动。
func TestParallelismZeroIsRejected(t *testing.T) {
	cfg := DefaultConfig()
	cfg.Sandbox.Parallelism = 0
	if err := cfg.Validate(); err == nil {
		t.Errorf("parallelism=0应拒绝")
	}
}

func TestSandboxHardeningConfig(t *testing.T) {
	for _, mutate := range []func(*Config){
		func(c *Config) { c.Sandbox.Backend = "auto" },
		func(c *Config) { c.Sandbox.HelperSocket = "" },
		func(c *Config) { c.Sandbox.QueueSize = 0 },
		func(c *Config) { c.Sandbox.MaxRequestBytes = 0 },
		func(c *Config) { c.Sandbox.Store.MaxTotalBytes = 1 },
		func(c *Config) { c.Sandbox.Store.Retention = 0 },
		func(c *Config) { c.Logging.Path = "" },
		func(c *Config) { c.Logging.Level = "TRACE" },
	} {
		c := DefaultConfig()
		mutate(&c)
		if e := c.Validate(); e == nil {
			t.Fatal("unsafe config accepted")
		}
	}
	if c := DefaultConfig(); c.Sandbox.Backend != "linux" || c.Sandbox.Parallelism <= 0 {
		t.Fatal("unsafe default")
	}
}

// 环境变量名是部署契约：compose 与部署清单按这些名字注入，改名等于改部署。
func TestEnvOverridesYAML(t *testing.T) {
	p := writeYAML(t, "sandbox:\n  parallelism: 3\n")
	t.Setenv("CHERRY_OJ_SANDBOX_STORE_MAX_BLOB_BYTES", "123456")
	t.Setenv("CHERRY_OJ_SANDBOX_PARALLELISM", "5")
	t.Setenv("CHERRY_OJ_LOGGING_LEVEL", "WARN")

	cfg, err := LoadConfig(p)
	if err != nil {
		t.Fatal(err)
	}
	if cfg.Sandbox.Store.MaxBlobBytes != 123456 {
		t.Errorf("maxBlobBytes=%d", cfg.Sandbox.Store.MaxBlobBytes)
	}
	if cfg.Sandbox.Parallelism != 5 {
		t.Errorf("parallelism=%d want 5（环境变量应当压过 YAML）", cfg.Sandbox.Parallelism)
	}
	if cfg.Logging.Level != "WARN" {
		t.Errorf("logging.level=%q", cfg.Logging.Level)
	}
	// 没写的项必须还是默认值，不能变成零值
	if cfg.Sandbox.QueueSize != DefaultConfig().Sandbox.QueueSize {
		t.Errorf("queueSize 被覆盖成了 %d，没写的项不该动", cfg.Sandbox.QueueSize)
	}
}

// judge 段配错不应妨碍 sandbox 启动——这正是拆分配置要换来的性质。
func TestJudgeSettingsDoNotBlockSandbox(t *testing.T) {
	t.Setenv("CHERRY_OJ_JUDGE_CLOCK_RATIO", "0")
	t.Setenv("CHERRY_OJ_JUDGE_TESTDATA_ROOT", "")
	if _, err := LoadConfig(""); err != nil {
		t.Fatalf("judge 段的取值不该影响 sandbox 启动: %v", err)
	}
}

// 示例配置文件必须能被加载且合法——否则它只是篇文档，不是可用的配置。
func TestExampleConfigLoads(t *testing.T) {
	cfg, err := LoadConfig("../sandbox.example.yaml")
	if err != nil {
		t.Fatalf("示例配置加载失败: %v", err)
	}
	if cfg.Sandbox.Backend != backend.NameDevHost {
		t.Errorf("backend=%q", cfg.Sandbox.Backend)
	}
}
