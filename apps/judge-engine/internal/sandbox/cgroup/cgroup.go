// Package cgroup 管理已委派子树中的单次 v2 执行组，不理解判题状态。
// 仅包含本项目实现；go-sandbox 的机制参考记录见 WORK-048 DESIGN-042。
package cgroup

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"
	"time"
)

// Limits 中 CPU 配额只限制使用速率；累计 CPU 预算由调用者监测 Snapshot。
type Limits struct {
	MemoryBytes  int64
	MaxProcesses int
	CPUQuotaNs   int64
	CPUPeriodNs  int64
}

func (l Limits) validate() error {
	if l.MemoryBytes <= 0 || l.MaxProcesses <= 0 || int64(l.MaxProcesses) > 2147483647 {
		return fmt.Errorf("cgroup memoryBytes/maxProcesses 必须为允许范围内的正数")
	}
	if l.CPUQuotaNs < 1_000_000 || l.CPUPeriodNs < 1_000_000 || l.CPUPeriodNs > 1_000_000_000 || l.CPUQuotaNs%1000 != 0 || l.CPUPeriodNs%1000 != 0 {
		return fmt.Errorf("cgroup CPU 配额/周期必须为整微秒，配额至少 1ms，周期 1ms～1s")
	}
	return nil
}

type Snapshot struct {
	CPUNs           int64
	MemoryBytes     int64
	OOM             uint64
	OOMKill         uint64
	MemoryMaxEvents uint64
	PidsMaxEvents   uint64
	Populated       bool
}

// filesystem 仅用于错误注入；生产实现锚定已验证为 cgroup2fs 的目录 FD。
type filesystem interface {
	read(string) ([]byte, error)
	write(string, string) error
	checkWritable(string) error
	mkdir(string) error
	remove(string) error
	open(string) (*os.File, error)
	close() error
}

type Manager struct {
	mu       sync.Mutex
	fs       filesystem
	groups   map[string]*Group
	closed   bool
	poisoned error
}

// Open 只打开已准备好的 jobs 子树，绝不开启祖先控制器或移动现有进程。
// 调用者须保证委派目录仅由本 helper 管理，且用户程序不能访问 cgroupfs。
func Open(path string) (*Manager, error) {
	fs, err := openFilesystem(path)
	if err != nil {
		return nil, err
	}
	m, err := newManager(fs)
	if err != nil {
		return nil, errors.Join(err, fs.close())
	}
	return m, nil
}

func newManager(fs filesystem) (*Manager, error) {
	for _, name := range []string{"cgroup.controllers", "cgroup.subtree_control"} {
		data, err := fs.read(name)
		if err != nil {
			return nil, fmt.Errorf("读取 %s: %w", name, err)
		}
		have := map[string]bool{}
		for _, v := range strings.Fields(string(data)) {
			have[v] = true
		}
		for _, v := range []string{"cpu", "memory", "pids"} {
			if !have[v] {
				return nil, fmt.Errorf("%s 缺 %s", name, v)
			}
		}
	}
	data, err := fs.read("cgroup.procs")
	if err != nil {
		return nil, err
	}
	if len(strings.TrimSpace(string(data))) != 0 {
		return nil, fmt.Errorf("委派内部节点存在进程，监督进程必须位于独立叶子")
	}
	data, err = fs.read("cgroup.type")
	if err != nil {
		return nil, err
	}
	if strings.TrimSpace(string(data)) != "domain" {
		return nil, fmt.Errorf("只支持 domain cgroup")
	}
	return &Manager{fs: fs, groups: make(map[string]*Group)}, nil
}

// New 使用不可复用的随机目录；任何设置失败都会尝试清理，失败则隔离管理器。
func (m *Manager) New(l Limits) (*Group, error) {
	if err := l.validate(); err != nil {
		return nil, err
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.closed {
		return nil, os.ErrClosed
	}
	if m.poisoned != nil {
		return nil, fmt.Errorf("cgroup 管理器已隔离: %w", m.poisoned)
	}
	// 删除已经关闭的对象引用，避免 1000 次执行留下 1000 份控制面对象。
	for name, g := range m.groups {
		g.mu.Lock()
		removed := g.removed
		cleanupErr := g.cleanupErr
		g.mu.Unlock()
		if cleanupErr != nil {
			m.poisoned = cleanupErr
			return nil, fmt.Errorf("任务组回收失败，拒绝新任务: %w", cleanupErr)
		}
		if removed {
			delete(m.groups, name)
		}
	}
	var nonce [16]byte
	if _, err := rand.Read(nonce[:]); err != nil {
		return nil, err
	}
	name := "run-" + hex.EncodeToString(nonce[:])
	if err := m.fs.mkdir(name); err != nil {
		return nil, fmt.Errorf("独占创建 cgroup %s: %w", name, err)
	}
	g := &Group{fs: m.fs, name: name}
	m.groups[name] = g
	if err := g.configure(l); err != nil {
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		cleanupErr := g.Close(ctx)
		if cleanupErr != nil {
			m.poisoned = cleanupErr
		}
		return nil, errors.Join(fmt.Errorf("配置 cgroup %s: %w", name, err), cleanupErr)
	}
	return g, nil
}

// Close 清理本管理器创建的组；失败时保留根句柄供重试，不递归删除未知组。
func (m *Manager) Close(ctx context.Context) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.closed = true
	var result error
	for _, g := range m.groups {
		result = errors.Join(result, g.Close(ctx))
	}
	if result != nil {
		return result
	}
	if m.fs == nil {
		return nil
	}
	err := m.fs.close()
	m.fs = nil
	return err
}

type Group struct {
	mu         sync.Mutex
	fs         filesystem
	name       string
	stopped    bool
	empty      bool
	removed    bool
	final      Snapshot
	finalErr   error
	cleanupErr error
}

// File 返回供 UseCgroupFD 使用的独立句柄，调用者必须在 Start 返回后关闭。
// 它不得通过 ExtraFiles 或任何 IPC 交给不可信程序。
func (g *Group) File() (*os.File, error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.stopped || g.removed {
		return nil, os.ErrClosed
	}
	return g.fs.open(g.name)
}

func (g *Group) configure(l Limits) error {
	settings := [][2]string{
		{"memory.max", fmt.Sprint(l.MemoryBytes)},
		{"memory.swap.max", "0"}, {"memory.oom.group", "1"},
		{"pids.max", fmt.Sprint(l.MaxProcesses)},
		{"cpu.max", fmt.Sprintf("%d %d", l.CPUQuotaNs/1000, l.CPUPeriodNs/1000)},
	}
	for _, setting := range settings {
		name := g.name + "/" + setting[0]
		if err := g.fs.write(name, setting[1]); err != nil {
			return fmt.Errorf("写入 %s: %w", setting[0], err)
		}
		data, err := g.fs.read(name)
		if err != nil {
			return err
		}
		if strings.Join(strings.Fields(string(data)), " ") != setting[1] {
			return fmt.Errorf("%s 限额读回不一致", setting[0])
		}
	}
	// 必需计量接口在用户进程启动前核验。
	snap, err := g.snapshot()
	if err != nil {
		return err
	}
	if snap.Populated || snap.CPUNs != 0 || snap.MemoryBytes != 0 || snap.OOM != 0 || snap.OOMKill != 0 || snap.MemoryMaxEvents != 0 || snap.PidsMaxEvents != 0 {
		return fmt.Errorf("新建 cgroup 已有进程或历史计量")
	}
	// 仅打开写句柄检查权限，不预写 kill。首站实测预写会使随后原子入组的子进程被杀。
	return g.fs.checkWritable(g.name + "/cgroup.kill")
}

func (g *Group) Snapshot() (Snapshot, error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.removed {
		return Snapshot{}, os.ErrClosed
	}
	if g.stopped && g.empty {
		return g.final, g.finalErr
	}
	return g.snapshot()
}

func (g *Group) snapshot() (Snapshot, error) {
	read := func(name string) ([]byte, error) { return g.fs.read(g.name + "/" + name) }
	cpu, err := read("cpu.stat")
	if err != nil {
		return Snapshot{}, err
	}
	peak, err := read("memory.peak")
	if err != nil {
		return Snapshot{}, err
	}
	memory, err := read("memory.events")
	if err != nil {
		return Snapshot{}, err
	}
	pids, err := read("pids.events")
	if err != nil {
		return Snapshot{}, err
	}
	events, err := read("cgroup.events")
	if err != nil {
		return Snapshot{}, err
	}
	return parseSnapshot(cpu, peak, memory, pids, events)
}

// Stop 终止全部后代并等待 populated=0，完成后才读取最终计量。
// 清理调用者应提供独立且有期限的上下文，不传入已取消的执行上下文。
func (g *Group) Stop(ctx context.Context) (Snapshot, error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	return g.stop(ctx)
}

func (g *Group) stop(ctx context.Context) (Snapshot, error) {
	if g.removed {
		return g.final, g.finalErr
	}
	if g.empty {
		return g.final, g.finalErr
	}
	if err := ctx.Err(); err != nil {
		return Snapshot{}, err
	}
	g.stopped = true
	if err := g.fs.write(g.name+"/cgroup.kill", "1"); err != nil {
		return Snapshot{}, fmt.Errorf("终止整组: %w", err)
	}
	ticker := time.NewTicker(5 * time.Millisecond)
	defer ticker.Stop()
	for {
		data, err := g.fs.read(g.name + "/cgroup.events")
		if err != nil {
			return Snapshot{}, err
		}
		fields, err := parseFields(data)
		if err != nil {
			return Snapshot{}, err
		}
		populated, err := required(fields, "populated")
		if err != nil {
			return Snapshot{}, err
		}
		if populated > 1 {
			return Snapshot{}, fmt.Errorf("无效 populated: %d", populated)
		}
		if populated == 0 {
			g.empty = true
			g.final, g.finalErr = g.snapshot()
			return g.final, g.finalErr
		}
		select {
		case <-ctx.Done():
			return Snapshot{}, ctx.Err()
		case <-ticker.C:
		}
	}
}

func (g *Group) Close(ctx context.Context) (result error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	defer func() {
		if result != nil {
			g.cleanupErr = result
		}
	}()
	if g.removed {
		return nil
	}
	_, err := g.stop(ctx)
	if !g.empty {
		return err
	} // 未证实无后代，禁止删除或回收槽位。
	removeErr := g.fs.remove(g.name)
	if removeErr == nil {
		g.removed = true
	}
	return errors.Join(err, removeErr)
}
