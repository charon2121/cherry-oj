package helper

import (
	"fmt"
	"io"
)

// executionResult 仅在 helper 内部拥有产物句柄。Result 是可序列化事实，不能持有宿主资源。
// 调用者取得 executionResult 后负责 Close；只有交付并成功关闭后才发送 Completion。
type executionResult struct {
	Result
	files []*ownedFile
}

func (r *executionResult) Close() error {
	err := closeFiles(r.files...)
	r.files = nil
	return err
}
func (r *executionResult) WriteFiles(w io.Writer) error {
	if len(r.files) != len(r.Outputs) {
		return fmt.Errorf("产物句柄与元数据不一致")
	}
	for i, f := range r.files {
		if _, err := io.CopyN(w, f, r.Outputs[i].SizeBytes); err != nil {
			return fmt.Errorf("交付产物 %s: %w", r.Outputs[i].Path, err)
		}
	}
	return nil
}
