package runner

import (
	"context"
	"errors"
	"fmt"
	"io"
	"strings"
	"sync"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/internal/backend"
	"cherry-oj/judge-engine/sandbox/internal/store"
)

// MaxInputBytes 是一次请求内所有输入文件和 stdin 共享的字节预算。
const MaxInputBytes int64 = 64 << 20

// sources 持有本次执行的全部输入句柄，直到 Execute 返回。
// 关闭必须能解除取消后仍阻塞的读取，因此只接受本地文件或内存流。
type sources struct {
	inputs []backend.NamedSource
	stdin  *backend.Source
	closes []func() error
	stop   func() bool
	once   sync.Once
	err    error
}

// openSources 解析 store 引用与内联文本，并让全部输入共享一份总量预算。
// 超限返回错误而不是伪装成 EOF，防止接受被截断的源码或输入。
func openSources(ctx context.Context, st store.Store, spec contract.RunSpec) (*sources, error) {
	s := &sources{}
	budget := &budget{remaining: MaxInputBytes}
	for name, src := range spec.Inputs {
		rc, err := resolve(st, src)
		if err != nil {
			return s, errors.Join(err, s.close())
		}
		s.track(rc)
		// 输入统一按可执行写入：判题把编译产物作为输入送回执行那一步。
		s.inputs = append(s.inputs, backend.NamedSource{
			Name:   name,
			Source: backend.Source{Reader: &budgetReader{r: rc, budget: budget}, Executable: true},
		})
	}
	if spec.Stdin != nil {
		rc, err := resolve(st, *spec.Stdin)
		if err != nil {
			return s, errors.Join(err, s.close())
		}
		s.track(rc)
		s.stdin = &backend.Source{Reader: &budgetReader{r: rc, budget: budget}}
	}
	// 取消后关闭句柄，解除仍阻塞的读取；close 时撤销回调，避免在收尾后重复触发。
	s.stop = context.AfterFunc(ctx, func() { s.close() })
	return s, nil
}

func (s *sources) track(rc io.ReadCloser) {
	s.closes = append(s.closes, sync.OnceValue(rc.Close))
}

func (s *sources) close() error {
	s.once.Do(func() {
		if s.stop != nil {
			s.stop()
		}
		for _, closeFn := range s.closes {
			s.err = errors.Join(s.err, closeFn())
		}
	})
	return s.err
}

// budget 让全部输入共享一份剩余字节数：单个文件不超限不代表总量不超限。
type budget struct{ remaining int64 }

type budgetReader struct {
	r      io.Reader
	budget *budget
}

func (r *budgetReader) Read(p []byte) (int, error) {
	if len(p) == 0 {
		return 0, nil
	}
	if r.budget.remaining == 0 {
		// 恰好到达上限还不能说明超限；额外探读一字节区分 EOF 与被截断的输入。
		var b [1]byte
		n, e := r.r.Read(b[:])
		if n > 0 {
			return 0, fmt.Errorf("total input size exceeds the limit")
		}
		return 0, e
	}
	if int64(len(p)) > r.budget.remaining {
		p = p[:r.budget.remaining]
	}
	n, e := r.r.Read(p)
	r.budget.remaining -= int64(n)
	return n, e
}

func resolve(st store.Store, src contract.FileSource) (io.ReadCloser, error) {
	switch {
	case src.Ref != "" && src.Text != "":
		return nil, fmt.Errorf("file source: ref and text are mutually exclusive")
	case src.Ref != "":
		rc, e := st.Get(src.Ref)
		if e != nil {
			return nil, fmt.Errorf("get %q: %w", src.Ref, e)
		}
		return rc, nil
	default:
		return io.NopCloser(strings.NewReader(src.Text)), nil
	}
}
