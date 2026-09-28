//go:build linux && amd64

package execution

import (
	"cherry-oj/judge-engine/internal/hostexec"
	"fmt"
	"io"
)

// Result 接管已结束执行的产物；hostexec.Result 自身只含可序列化事实。
// 交付者必须成功关闭产物后才能发送 hostexec.Completion。
type Result struct {
	hostexec.Result
	artifacts *artifactSet
}

func (r *Result) Close() error { return r.artifacts.Close() }

// CopyN 将短文件视为交付失败，避免下一帧被当成剩余文件内容。
func (r *Result) WriteFiles(w io.Writer) error {
	var files []*ownedFile
	if r.artifacts != nil {
		files = r.artifacts.files
	}
	if len(files) != len(r.Outputs) {
		return fmt.Errorf("artifact handle disagrees with its metadata")
	}
	for i, file := range files {
		if _, err := io.CopyN(w, file, r.Outputs[i].SizeBytes); err != nil {
			return fmt.Errorf("deliver artifact %s: %w", r.Outputs[i].Path, err)
		}
	}
	return nil
}
