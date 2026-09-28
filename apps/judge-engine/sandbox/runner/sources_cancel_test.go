// 白盒测试：使用既有执行替身，在解析输入期间取消以覆盖 AfterFunc 登记与执行的竞态。
package runner

import (
	"context"
	"io"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
)

type cancelOnGetStore struct{ cancel context.CancelFunc }

func (s cancelOnGetStore) Get(string) (io.ReadCloser, error) {
	s.cancel()
	return io.NopCloser(strings.NewReader("input")), nil
}
func (s cancelOnGetStore) Put(io.Reader) (string, error) { return "", nil }
func (s cancelOnGetStore) Delete(string) error           { return nil }
func TestCancellationDuringSourceOpen(t *testing.T) {
	for i := 0; i < 10000; i++ {
		ctx, cancel := context.WithCancel(context.Background())
		New(&lifecycleBackend{}, cancelOnGetStore{cancel}).Run(ctx, contract.RunSpec{Command: []string{"true"}, Stdin: &contract.FileSource{Ref: "input"}})
		cancel()
	}
}
