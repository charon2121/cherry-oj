package execution

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"os"
	"runtime"
	"time"

	"cherry-oj/judge-engine/execution/backend"
	"cherry-oj/judge-engine/execution/pool"
	"cherry-oj/judge-engine/execution/runner"
	"cherry-oj/judge-engine/execution/store"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/platform/config"
)

// Version 是执行层对外自报的名字与版本；节点自检据此确认执行层来自本项目。
const (
	Name    = "cherry-oj-sandbox"
	Version = "0.1.0-mvp"
)

// EngineSettings 是执行层自己的配置：并发、排队、执行后端与临时文件存储。它不含任何网络设置，
// 装配方（judge）决定把它放在自己配置的哪一段。
type EngineSettings struct {
	// Parallelism：正数并发容量，显式 0 拒绝启动。
	Parallelism int    `yaml:"parallelism"`
	QueueSize   int    `yaml:"queueSize"`
	Backend     string `yaml:"backend"`
	// ExecutorPath：setuid-root 安装的 C 执行器（apps/sandbox）。BoxesRoot：交给执行器的 box 目录根，
	// 本进程独占，权限 0700。两者都只在 linux 后端使用，且须与执行器受信配置中的 boxes 一致。
	ExecutorPath string `yaml:"executorPath"`
	BoxesRoot    string `yaml:"boxesRoot"`
	// AllowUnsafeBackend：显式承认使用零隔离后端。默认关闭，缺省即拒绝启动——
	// devhost 不提供任何隔离，误用它跑用户提交等于没有沙箱，这一步必须是一次自觉的配置。
	AllowUnsafeBackend bool  `yaml:"allowUnsafeBackend"`
	Store              Store `yaml:"store"`
}

type Store struct {
	// Root：本进程独占的私有 blob 目录，由配置显式指定。
	Root string `yaml:"root"`
	// MaxBlobBytes：单个 blob 的上限。/dev/shm 是内存盘，没有上限一次大上传就能撑爆 RAM。
	MaxBlobBytes  int64           `yaml:"maxBlobBytes"`
	MaxTotalBytes int64           `yaml:"maxTotalBytes"`
	MaxEntries    int             `yaml:"maxEntries"`
	Retention     config.Duration `yaml:"retention"`
}

// DefaultEngineSettings 返回有界的默认值；Linux 隔离仍需先以 setuid-root 安装执行器。
func DefaultEngineSettings() EngineSettings {
	return EngineSettings{
		Parallelism:  1,
		QueueSize:    8,
		Backend:      backend.NameLinux,
		ExecutorPath: "/var/lib/cherry-sandbox/current/libexec/sandbox",
		BoxesRoot:    "./data/sandbox-boxes",
		Store: Store{
			Root:          "./data/sandbox-blobs",
			MaxBlobBytes:  64 << 20,
			MaxTotalBytes: 512 << 20,
			MaxEntries:    4096,
			Retention:     config.Duration(time.Hour),
		},
	}
}

// Validate 把「配错了」挡在启动时：宁可起不来，也别悄悄跑错。prefix 是这段配置在装配方
// 配置里的路径，只用于让错误信息指向用户实际要改的键。
func (s EngineSettings) Validate(prefix string) error {
	if s.Parallelism <= 0 || s.Parallelism > 256 {
		return fmt.Errorf("%s.parallelism must be 1 to 256, got %d", prefix, s.Parallelism)
	}
	if s.Store.MaxBlobBytes <= 0 || s.Store.MaxBlobBytes > 64<<20 {
		return fmt.Errorf("%s.store.maxBlobBytes must be 1 to 64MiB, got %d", prefix, s.Store.MaxBlobBytes)
	}
	if s.Backend != backend.NameLinux && s.Backend != backend.NameDevHost {
		return fmt.Errorf("%s.backend must be %s or %s", prefix, backend.NameLinux, backend.NameDevHost)
	}
	if s.Backend == backend.NameDevHost && !s.AllowUnsafeBackend {
		return fmt.Errorf("the %s backend provides no isolation; enabling it requires setting %s.allowUnsafeBackend explicitly", backend.NameDevHost, prefix)
	}
	if s.Backend == backend.NameLinux && (s.ExecutorPath == "" || s.BoxesRoot == "" || s.Store.Root == "") {
		return fmt.Errorf("the linux backend requires %s.executorPath, boxesRoot and store.root", prefix)
	}
	// 每次执行占用一个 box，执行器最多支持 4 个。
	if s.Backend == backend.NameLinux && s.Parallelism > 4 {
		return fmt.Errorf("the linux backend supports at most 4 parallel executions, got %d", s.Parallelism)
	}
	if s.QueueSize <= 0 || s.QueueSize > 1024 {
		return fmt.Errorf("%s.queueSize must be 1 to 1024, got %d", prefix, s.QueueSize)
	}
	if s.Store.MaxTotalBytes < s.Store.MaxBlobBytes || s.Store.MaxEntries <= 0 || s.Store.Retention <= 0 {
		return fmt.Errorf("invalid %s.store total/entry/retention", prefix)
	}
	return nil
}

// Engine 是进程内的执行层：blob 存储、排队与并发、限额归一化、执行后端。
// 它不懂判题——只回答「这条命令执行完了、产物在哪」。
type Engine struct {
	store        managedStore
	closeBackend func() error
	pool         *pool.Pool
	stopSweeper  func()
	isolation    string
}

// Open 装配执行层。linux 后端在返回前用一次真实执行验证整条链，失败即不可用。
// 返回错误时已经释放了所有打开的资源。
func Open(s EngineSettings, logger *slog.Logger) (e *Engine, err error) {
	e = &Engine{isolation: s.Backend}
	defer func() {
		if err != nil {
			err = errors.Join(err, e.Close())
			e = nil
		}
	}()
	// 先落到具体类型再赋给接口：失败时的 nil 指针不能变成非 nil 的接口值，否则 Close 会解引用它。
	st, err := store.New(s.Store.Root, store.Options{MaxBlobBytes: s.Store.MaxBlobBytes,
		MaxTotalBytes: s.Store.MaxTotalBytes, MaxEntries: s.Store.MaxEntries, Retention: s.Store.Retention.Std()})
	if err != nil {
		return e, fmt.Errorf("open store: %w", err)
	}
	e.store = st
	b, closeBackend, err := selectBackend(s)
	if err != nil {
		return e, err
	}
	e.closeBackend = closeBackend
	if e.pool, err = pool.New(runner.New(b, e.store), pool.Options{Parallelism: s.Parallelism, QueueSize: s.QueueSize, Logger: logger}); err != nil {
		return e, err
	}
	if err = probeBackend(e.pool, s.Backend, logger); err != nil {
		return e, err
	}
	e.stopSweeper = startSweeper(e.store, logger)
	return e, nil
}

// Upload 保存一袋字节，返回之后 Run 可以引用的 ref。
func (e *Engine) Upload(_ context.Context, r io.Reader) (string, error) { return e.store.Put(r) }

// Delete 删除 ref；不存在的 ref 视为已删除。
func (e *Engine) Delete(_ context.Context, ref string) error {
	if err := e.store.Delete(ref); err != nil && !errors.Is(err, store.ErrNotFound) {
		return err
	}
	return nil
}

// Run 执行一条命令。error 只表示这次没能执行（请求不合法、排队已满、执行层已停止）；
// 命令跑得怎么样由 RunResult.Status 表达。
func (e *Engine) Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error) {
	if len(spec.Command) == 0 {
		return contract.RunResult{}, errors.New("command must not be empty")
	}
	if err := spec.Limits.Validate(); err != nil {
		return contract.RunResult{}, err
	}
	return e.pool.Run(ctx, spec)
}

// Version 自报名字、版本与当前隔离后端。
func (e *Engine) Version(context.Context) (contract.SandboxVersion, error) {
	return contract.SandboxVersion{Name: Name, Version: Version, Isolation: e.isolation}, nil
}

// Stopped 在执行层停止接单时关闭：回收未确认使池中毒，或 Close 已被调用。
// 装配方据此让整个进程退出，而不是继续以「一连串平台错误」的形式在线。
func (e *Engine) Stopped() <-chan struct{} { return e.pool.Stopped() }

// Close 逆序释放：先结束执行池（取消并确认在途执行的回收），再停 blob 清理、关后端、关存储。
// 在途执行可能仍在回滚产物引用，不能先关存储。汇总的错误非空时，说明有回收没有得到确认，
// 调用方应以失败退出。可重复调用。
func (e *Engine) Close() error {
	var err error
	if e.pool != nil {
		err = errors.Join(err, e.pool.Close())
	}
	if e.stopSweeper != nil {
		e.stopSweeper()
		e.stopSweeper = nil
	}
	if e.closeBackend != nil {
		err = errors.Join(err, e.closeBackend())
		e.closeBackend = nil
	}
	if e.store != nil {
		err = errors.Join(err, e.store.Close())
		e.store = nil
	}
	return err
}

// Store 的生命周期由 Engine 拥有，Pool 与 runner 不擅自关闭共享 Store。
type managedStore interface {
	store.Store
	io.Closer
	Sweep() error
}

// probeBackend 用一次真实执行（命令 true）验证 linux 后端整条链可用。
func probeBackend(p *pool.Pool, name string, logger *slog.Logger) error {
	if name != backend.NameLinux {
		return nil
	}
	probe, probeErr := p.Run(context.Background(), contract.RunSpec{Command: []string{"true"}})
	if probeErr != nil || probe.Status != contract.StatusOK {
		logger.Error("process.backend.probe.failed", "error", probeErr, "result", probe)
		return errors.Join(fmt.Errorf("isolated backend startup probe failed: status=%s", probe.Status), probeErr)
	}
	return nil
}

// startSweeper 每分钟清理一次过期 blob，返回的函数停止它并等待退出。
func startSweeper(st managedStore, logger *slog.Logger) (stop func()) {
	gcCtx, gcCancel := context.WithCancel(context.Background())
	gcDone := make(chan struct{})
	go func() {
		defer close(gcDone)
		ticker := time.NewTicker(time.Minute)
		defer ticker.Stop()
		for {
			select {
			case <-gcCtx.Done():
				return
			case <-ticker.C:
				if e := st.Sweep(); e != nil {
					logger.Error("store.sweep.failed", "error", e)
				}
			}
		}
	}()
	return func() { gcCancel(); <-gcDone }
}

// selectBackend 同时交付执行后端和它的关闭函数。
// 隔离后端启动失败必须暴露错误，不能悄悄切到没有隔离保证的 devhost。
func selectBackend(c EngineSettings) (backend.Backend, func() error, error) {
	switch c.Backend {
	case backend.NameDevHost:
		// 配置校验已经要求显式承认；这里再挡一次，避免绕过校验直接装配。
		if !c.AllowUnsafeBackend {
			return nil, nil, fmt.Errorf("the %s backend provides no isolation; it requires setting allowUnsafeBackend explicitly", backend.NameDevHost)
		}
		return backend.NewDevHost(), func() error { return nil }, nil
	case backend.NameLinux:
		if runtime.GOOS != "linux" || runtime.GOARCH != "amd64" {
			return nil, nil, fmt.Errorf("this platform does not support the Linux isolation backend")
		}
		if os.Geteuid() == 0 {
			return nil, nil, fmt.Errorf("the execution layer must run as non-root; privilege is held only by the setuid executor")
		}
		b, err := backend.NewExecutor(c.ExecutorPath, c.BoxesRoot, c.Parallelism)
		if err != nil {
			return nil, nil, err
		}
		return b, b.Close, nil
	default:
		return nil, nil, fmt.Errorf("unknown backend: %s", c.Backend)
	}
}
