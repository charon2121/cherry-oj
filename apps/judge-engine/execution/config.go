package execution

import (
	"fmt"

	"cherry-oj/judge-engine/internal/platform/config"
)

// Config 是 sandbox 服务的全部运行配置。
//
// 两个小节对应环境变量前缀 `CHERRY_OJ_LOGGING_*` 与 `CHERRY_OJ_SANDBOX_*`。
// **YAML 键名即部署契约**：compose 与部署清单按这些名字注入环境变量，改名等于改部署。
type Config struct {
	Logging config.Logging `yaml:"logging"`
	Sandbox Settings       `yaml:"sandbox"`
}

// Settings 是 sandbox 段的运行策略。
type Settings struct {
	HTTPAddr string `yaml:"httpAddr"`
	// Parallelism：正数并发容量，显式 0 拒绝启动。
	Parallelism int    `yaml:"parallelism"`
	Store       Store  `yaml:"store"`
	Backend     string `yaml:"backend"`
	// ExecutorPath：setuid-root 安装的 C 执行器（apps/sandbox）。BoxesRoot：交给执行器的 box 目录根，
	// 本服务独占，权限 0700。两者都只在 linux 后端使用，且须与执行器受信配置中的 boxes 一致。
	ExecutorPath    string `yaml:"executorPath"`
	BoxesRoot       string `yaml:"boxesRoot"`
	QueueSize       int    `yaml:"queueSize"`
	MaxRequestBytes int64  `yaml:"maxRequestBytes"`
	// AllowUnsafeBackend：显式承认使用零隔离后端。默认关闭，缺省即拒绝启动——
	// devhost 不提供任何隔离，误用它跑用户提交等于没有沙箱，这一步必须是一次自觉的配置。
	AllowUnsafeBackend bool `yaml:"allowUnsafeBackend"`
}

// DefaultConfig 返回 sandbox 的有界默认配置；Linux 隔离仍需先以 setuid-root 安装执行器。
func DefaultConfig() Config {
	e := DefaultEngineSettings()
	return Config{
		Logging: config.Logging{
			Path:  "./logs",
			Level: "INFO",
		},
		Sandbox: Settings{
			HTTPAddr:           "127.0.0.1:5050",
			MaxRequestBytes:    2 << 20,
			Parallelism:        e.Parallelism,
			QueueSize:          e.QueueSize,
			Backend:            e.Backend,
			ExecutorPath:       e.ExecutorPath,
			BoxesRoot:          e.BoxesRoot,
			AllowUnsafeBackend: e.AllowUnsafeBackend,
			Store:              e.Store,
		},
	}
}

// Validate 把「配错了」挡在启动时：宁可起不来，也别悄悄跑错。
func (c Config) Validate() error {
	if err := c.Logging.Validate(); err != nil {
		return err
	}
	s := c.Sandbox
	if s.HTTPAddr == "" {
		return fmt.Errorf("sandbox.httpAddr must not be empty")
	}
	if s.MaxRequestBytes <= 0 || s.MaxRequestBytes > 8<<20 {
		return fmt.Errorf("invalid sandbox request body limit")
	}
	if err := s.engine().Validate("sandbox"); err != nil {
		return err
	}
	// 跨层期限的顺序关系也在启动时挡住：配错了不会报错，只会在某次长执行时表现成平台错误。
	return budget()
}

// engine 取出执行层自己的配置。sandbox 服务的配置键是部署契约（环境变量由键名推出），
// 所以这里保持原来的平铺结构，而不是嵌套一段 execution。
func (s Settings) engine() EngineSettings {
	return EngineSettings{Parallelism: s.Parallelism, QueueSize: s.QueueSize, Backend: s.Backend,
		ExecutorPath: s.ExecutorPath, BoxesRoot: s.BoxesRoot, AllowUnsafeBackend: s.AllowUnsafeBackend, Store: s.Store}
}

// LoadConfig 按「默认值 → YAML → 环境变量」装配 sandbox 配置并校验。
func LoadConfig(path string) (Config, error) { return config.Load(path, DefaultConfig()) }
