package backend

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"syscall"
)

// Executor 通过 setuid 的 sandbox 执行器（apps/sandbox）执行命令。本进程不持有任何特权。
//
// 每次执行占用一个 box：把请求、输入与 stdin 写进 box 目录，运行 `sandbox --box N`，读回一行
// JSON 事实，再从 box 的 out/ 取回 stdout、stderr 与产物。box 目录布局与执行器的约定见
// apps/sandbox/README.md。执行器报告回收未确认时，这个 box 不再归还：残留的进程可能还在，
// 容量只能缩小，不能复用。
type Executor struct {
	binary string
	root   string
	lock   *os.File
	boxes  chan int
}

// 执行器的退出码约定，见 apps/sandbox/README.md。
const (
	executorRefused     = 1
	executorUnreclaimed = 2
)

// NewExecutor 在 root 下独占地建立 count 个 box。root 必须是本服务身份拥有的 0700 目录，
// 执行器以 root 身份只接受属于服务身份的 box。
func NewExecutor(binary, root string, count int) (*Executor, error) {
	if err := checkExecutorBinary(binary); err != nil {
		return nil, err
	}
	return openExecutor(binary, root, count)
}

// checkExecutorBinary 只是启动时的明确报错：执行器没有以 setuid-root 安装时，
// 每次执行都会失败，不如一开始就拒绝启动。
func checkExecutorBinary(binary string) error {
	if !filepath.IsAbs(binary) {
		return fmt.Errorf("the executor path must be absolute: %q", binary)
	}
	info, err := os.Stat(binary)
	if err != nil {
		return err
	}
	stat, ok := info.Sys().(*syscall.Stat_t)
	if !info.Mode().IsRegular() || info.Mode()&os.ModeSetuid == 0 || !ok || stat.Uid != 0 {
		return fmt.Errorf("the executor %s must be a setuid-root regular file", binary)
	}
	return nil
}

func openExecutor(binary, root string, count int) (*Executor, error) {
	if count < 1 || count > 4 {
		return nil, fmt.Errorf("the executor supports 1 to 4 boxes, got %d", count)
	}
	if err := os.MkdirAll(root, 0o700); err != nil {
		return nil, err
	}
	info, err := os.Lstat(root)
	if err != nil {
		return nil, err
	}
	if stat, ok := info.Sys().(*syscall.Stat_t); !info.IsDir() || info.Mode().Perm() != 0o700 || !ok || int(stat.Uid) != os.Geteuid() {
		return nil, fmt.Errorf("the box root must be a 0700 directory owned by the service")
	}
	lock, err := os.OpenFile(filepath.Join(root, ".lock"), os.O_CREATE|os.O_RDWR|syscall.O_NOFOLLOW, 0o600)
	if err != nil {
		return nil, err
	}
	if err = syscall.Flock(int(lock.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil {
		lock.Close()
		return nil, fmt.Errorf("the box root is in use by another process: %w", err)
	}
	e := &Executor{binary: binary, root: root, lock: lock, boxes: make(chan int, count)}
	for i := 0; i < count; i++ {
		if err := e.reset(i); err != nil {
			lock.Close()
			return nil, err
		}
		e.boxes <- i
	}
	return e, nil
}

// Close 释放 box 根的独占锁，必须在所有执行结束之后调用。
func (e *Executor) Close() error { return e.lock.Close() }

func (e *Executor) boxDir(i int) string { return filepath.Join(e.root, strconv.Itoa(i)) }

// reset 把 box 恢复成空的初始状态。上一次执行留下的产物只属于上一次。
func (e *Executor) reset(i int) error {
	dir := e.boxDir(i)
	if err := os.RemoveAll(dir); err != nil {
		return err
	}
	for _, d := range []string{dir, filepath.Join(dir, "in"), filepath.Join(dir, "out")} {
		if err := os.Mkdir(d, 0o700); err != nil {
			return err
		}
	}
	return nil
}

func (e *Executor) Execute(ctx context.Context, j Job, sink OutputSink) (Facts, error) {
	var box int
	select {
	case box = <-e.boxes:
	case <-ctx.Done():
		return Facts{}, ctx.Err()
	}
	facts, reclaimed, err := e.execute(ctx, box, j, sink)
	if reclaimed {
		// 交付完就清空 box：本次的源码、输入与产物不该在磁盘上多停留。清不掉的 box 不再复用。
		if resetErr := e.reset(box); resetErr != nil {
			return facts, errors.Join(err, cleanupFailed("clear box %d: %w", box, resetErr))
		}
		e.boxes <- box
	}
	return facts, err
}

func (e *Executor) execute(ctx context.Context, box int, j Job, sink OutputSink) (Facts, bool, error) {
	if err := e.reset(box); err != nil {
		return Facts{}, false, cleanupFailed("reset box %d: %w", box, err)
	}
	dir := e.boxDir(box)
	if err := writeRequest(dir, j); err != nil {
		return Facts{}, true, err
	}
	out, code, err := e.run(ctx, box)
	switch {
	case err != nil:
		// 执行器没能按约定退出（被信号杀死等），执行组可能残留，只能按回收未确认处理。
		return Facts{}, false, cleanupFailed("sandbox executor: %w", err)
	case code == executorUnreclaimed:
		return Facts{}, false, cleanupFailed("sandbox executor could not reclaim box %d: %s", box, out.stderr)
	case code == executorRefused:
		return Facts{}, true, fmt.Errorf("sandbox executor refused the request: %s", out.stderr)
	case code != 0:
		return Facts{}, false, cleanupFailed("sandbox executor exited with unexpected code %d", code)
	}
	reported, err := decodeFacts(out.stdout)
	if err != nil {
		return Facts{}, true, err
	}
	facts := reported.toFacts()
	if reported.Error != "" {
		return facts, true, errors.New(reported.Error)
	}
	if err := deliverStreams(dir, j, reported); err != nil {
		return facts, true, err
	}
	return facts, true, deliverOutputs(dir, j, reported, facts, sink)
}
