package runner

import (
	"bytes"
	"errors"
	"fmt"
	"io"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/internal/backend"
	"cherry-oj/judge-engine/sandbox/internal/store"
)

// MaxInlineBytes 限制一次响应内所有内联产物的累计大小。
const MaxInlineBytes int64 = 1 << 20

// collector 是交给后端的产物接收端。它在交付当场决定是否保留：
// 后端把执行事实一并给出，因此一次超时或非零退出的执行不会把产物写进 Store 再删掉。
type collector struct {
	store     store.Store
	inline    map[string]bool
	artifact  map[string]bool
	limits    contract.Limits
	overflow  func() bool
	ctxErr    func() error
	outputs   map[string]string
	artifacts map[string]string
	remaining int64
	err       error
}

func newCollector(st store.Store, spec contract.RunSpec, limits contract.Limits,
	overflow func() bool, ctxErr func() error) *collector {
	c := &collector{store: st, limits: limits, overflow: overflow, ctxErr: ctxErr,
		inline: map[string]bool{}, artifact: map[string]bool{},
		outputs: map[string]string{}, artifacts: map[string]string{},
		remaining: MaxInlineBytes}
	for _, name := range spec.Outputs {
		c.inline[name] = true
	}
	for _, name := range spec.Artifacts {
		c.artifact[name] = true
	}
	return c
}

// accept 实现 backend.OutputSink。返回错误会中止交付，因此这里只在真正无法保留产物时报错；
// 「本次执行结论不是 OK」不是错误，直接丢弃即可。
func (c *collector) accept(facts backend.Facts, name string, r io.Reader) error {
	if classify(c.limits, facts, c.overflow(), c.ctxErr()) != contract.StatusOK {
		return nil
	}
	switch {
	case c.inline[name] && c.artifact[name]:
		// 同一文件既要内联又要持久化：只读一遍，两处共用同一份字节。
		data, err := c.readInline(name, r)
		if err != nil {
			return err
		}
		return c.put(name, bytes.NewReader(data))
	case c.inline[name]:
		_, err := c.readInline(name, r)
		return err
	case c.artifact[name]:
		return c.put(name, r)
	}
	return nil
}

func (c *collector) readInline(name string, r io.Reader) ([]byte, error) {
	data, err := io.ReadAll(io.LimitReader(r, c.remaining+1))
	if err != nil {
		return nil, err
	}
	if int64(len(data)) > c.remaining {
		return nil, fmt.Errorf("内联产物总量超过%d bytes", MaxInlineBytes)
	}
	c.remaining -= int64(len(data))
	c.outputs[name] = string(data)
	return data, nil
}

func (c *collector) put(name string, r io.Reader) error {
	ref, err := c.store.Put(r)
	// Put 成功后即登记，随后失败也可以回滚。
	if err != nil {
		return err
	}
	c.artifacts[name] = ref
	return nil
}

// publish 把已收集的产物并入结果。缺少声明过的产物同样是失败——
// 静默少给一个文件会让下一步拿着不完整的工作区继续跑。
func (c *collector) publish(spec contract.RunSpec, result contract.RunResult) contract.RunResult {
	err := c.err
	for _, name := range spec.Outputs {
		if _, ok := c.outputs[name]; !ok {
			err = errors.Join(err, fmt.Errorf("未交付产物: %q", name))
		}
	}
	for _, name := range spec.Artifacts {
		if _, ok := c.artifacts[name]; !ok {
			err = errors.Join(err, fmt.Errorf("未交付产物: %q", name))
		}
	}
	if err != nil {
		return rejectArtifacts(c.store, c.withCollected(result), err)
	}
	return c.withCollected(result)
}

func (c *collector) withCollected(result contract.RunResult) contract.RunResult {
	result.Outputs, result.Artifacts = c.outputs, c.artifacts
	return result
}

// discard 在结论不是 OK 时清掉已登记的引用；正常路径下 accept 根本不会保留它们，
// 这里只兜住「分类在交付之后又变化」的情况。
func (c *collector) discard() error {
	var err error
	for _, ref := range c.artifacts {
		err = errors.Join(err, c.store.Delete(ref))
	}
	c.outputs, c.artifacts = map[string]string{}, map[string]string{}
	return err
}
