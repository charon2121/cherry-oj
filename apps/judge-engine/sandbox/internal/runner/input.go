package runner

import (
	"context"
	"errors"
	"fmt"
	"io"
	"strings"
	"sync"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/internal/container"
	"cherry-oj/judge-engine/sandbox/internal/store"
)

// MaxInputBytes 是一次请求内所有输入文件和 stdin 共享的字节预算。
const MaxInputBytes int64 = 64 << 20

type commandInput struct {
	reader io.Reader
	close  func() error
}

func prepareInputs(ctx context.Context, c container.Container, st store.Store, spec contract.RunSpec) (commandInput, error) {
	remaining := MaxInputBytes
	for name, src := range spec.Inputs {
		if err := putInput(ctx, c, st, name, src, &remaining); err != nil {
			return commandInput{}, err
		}
	}
	if spec.Stdin == nil {
		return commandInput{close: func() error { return nil }}, nil
	}
	stdin, err := resolve(st, *spec.Stdin)
	if err != nil {
		return commandInput{}, err
	}
	// 输入源限于本地文件/内存；关闭可解除取消后的阻塞读取。
	closeInput := sync.OnceValue(stdin.Close)
	stop := context.AfterFunc(ctx, func() { closeInput() })
	return commandInput{
		reader: &budgetReader{r: stdin, remaining: remaining},
		close:  func() error { stop(); return closeInput() },
	}, nil
}

func putInput(ctx context.Context, c container.Container, st store.Store, name string, src contract.FileSource, remaining *int64) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	rc, err := resolve(st, src)
	if err != nil {
		return err
	}
	closeInput := sync.OnceValue(rc.Close)
	stop := context.AfterFunc(ctx, func() { closeInput() })
	defer stop()
	r := &budgetReader{r: rc, remaining: *remaining}
	err = c.PutFile(name, r, 0o755)
	*remaining = r.remaining
	return errors.Join(err, closeInput(), ctx.Err())
}

// 超限返回错误而不是伪装成EOF，防止接受被截断的源码/输入。
type budgetReader struct {
	r         io.Reader
	remaining int64
}

func (r *budgetReader) Read(p []byte) (int, error) {
	if len(p) == 0 {
		return 0, nil
	}
	if r.remaining == 0 {
		// 恰好到达上限还不能说明超限；额外探读一字节区分 EOF 与被截断的输入。
		var b [1]byte
		n, e := r.r.Read(b[:])
		if n > 0 {
			return 0, fmt.Errorf("输入总量超限")
		}
		return 0, e
	}
	if int64(len(p)) > r.remaining {
		p = p[:r.remaining]
	}
	n, e := r.r.Read(p)
	r.remaining -= int64(n)
	return n, e
}

func resolve(st store.Store, src contract.FileSource) (io.ReadCloser, error) {
	switch {
	case src.Ref != "" && src.Text != "":
		return nil, fmt.Errorf("file source: ref & text只能二选一")
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
