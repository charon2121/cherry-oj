package sandbox

import (
	"fmt"
	"time"

	"cherry-oj/judge-engine/internal/platform/config"
	"cherry-oj/judge-engine/sandbox/internal/backend"
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
	Parallelism     int    `yaml:"parallelism"`
	Store           Store  `yaml:"store"`
	Backend         string `yaml:"backend"`
	HelperSocket    string `yaml:"helperSocket"`
	WorkspaceRoot   string `yaml:"workspaceRoot"`
	QueueSize       int    `yaml:"queueSize"`
	MaxRequestBytes int64  `yaml:"maxRequestBytes"`
	// AllowUnsafeBackend：显式承认使用零隔离后端。默认关闭，缺省即拒绝启动——
	// devhost 不提供任何隔离，误用它跑用户提交等于没有沙箱，这一步必须是一次自觉的配置。
	AllowUnsafeBackend bool `yaml:"allowUnsafeBackend"`
}

type Store struct {
	// Root：服务独占的私有 blob 目录，由配置显式指定。
	Root string `yaml:"root"`
	// MaxBlobBytes：单次上传的上限。/dev/shm 是内存盘，没有上限一次大上传就能撑爆 RAM。
	MaxBlobBytes  int64           `yaml:"maxBlobBytes"`
	MaxTotalBytes int64           `yaml:"maxTotalBytes"`
	MaxEntries    int             `yaml:"maxEntries"`
	Retention     config.Duration `yaml:"retention"`
}

// DefaultConfig 返回 sandbox 的有界默认配置；Linux 隔离仍需先准备 helper 和权限。
func DefaultConfig() Config {
	return Config{
		Logging: config.Logging{
			Path:  "./logs",
			Level: "INFO",
		},
		Sandbox: Settings{
			HTTPAddr:        "127.0.0.1:5050",
			Parallelism:     1,
			Backend:         backend.NameLinux,
			HelperSocket:    "/run/cherry-sandbox/helper.sock",
			WorkspaceRoot:   "./data/sandbox-work",
			QueueSize:       8,
			MaxRequestBytes: 2 << 20,
			Store: Store{
				Root:          "./data/sandbox-blobs",
				MaxBlobBytes:  64 << 20,
				MaxTotalBytes: 512 << 20,
				MaxEntries:    4096,
				Retention:     config.Duration(time.Hour),
			},
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
		return fmt.Errorf("sandbox.httpAddr 不能为空")
	}
	if s.Parallelism <= 0 || s.Parallelism > 256 {
		return fmt.Errorf("sandbox.parallelism 必须为1～256，得到 %d", s.Parallelism)
	}
	if s.Store.MaxBlobBytes <= 0 || s.Store.MaxBlobBytes > 64<<20 {
		return fmt.Errorf("sandbox.store.maxBlobBytes 必须为1～64MiB，得到 %d", s.Store.MaxBlobBytes)
	}
	if s.Backend != backend.NameLinux && s.Backend != backend.NameDevHost {
		return fmt.Errorf("sandbox.backend必须为%s或%s", backend.NameLinux, backend.NameDevHost)
	}
	if s.Backend == backend.NameDevHost && !s.AllowUnsafeBackend {
		return fmt.Errorf("%s 后端不提供任何隔离，启用它必须显式设置 sandbox.allowUnsafeBackend", backend.NameDevHost)
	}
	if s.Backend == backend.NameLinux && (s.HelperSocket == "" || s.WorkspaceRoot == "" || s.Store.Root == "") {
		return fmt.Errorf("linux后端需要helperSocket、workspaceRoot和store.root")
	}
	if s.QueueSize <= 0 || s.QueueSize > 1024 || s.MaxRequestBytes <= 0 || s.MaxRequestBytes > 8<<20 {
		return fmt.Errorf("sandbox排队或请求体上限无效")
	}
	if s.Store.MaxTotalBytes < s.Store.MaxBlobBytes || s.Store.MaxEntries <= 0 || s.Store.Retention <= 0 {
		return fmt.Errorf("sandbox.store总量/条目/保留期无效")
	}
	// 跨层期限的顺序关系也在启动时挡住：配错了不会报错，只会在某次长执行时表现成平台错误。
	return budget()
}

// LoadConfig 按「默认值 → YAML → 环境变量」装配 sandbox 配置并校验。
func LoadConfig(path string) (Config, error) { return config.Load(path, DefaultConfig()) }
