package helper

import (
	"errors"
	"fmt"
	"io"
	"os"

	"cherry-oj/judge-engine/internal/sandbox/launcher"
)

// artifactSource 是已确认停止的工作区，Open 仍须验证链接、文件类型和挂载边界。
type artifactSource interface {
	Open(string) (*os.File, int64, error)
	Close() error
}
type workspaceDirectory struct{ File *ownedFile }

func (d workspaceDirectory) Close() error { return d.File.Close() }

// artifactSet 聚合已验证的句柄及长度，部分打开失败也保留已取得的句柄供关闭。
type artifactSet struct {
	outputs  []Output
	files    []*ownedFile
	closed   bool
	closeErr error
}

func collectArtifacts(source artifactSource, names []string) (*artifactSet, error) {
	set := &artifactSet{}
	var total int64
	for _, name := range names {
		file, size, err := source.Open(name)
		// 声明的产物可未生成，runner 在实际请求 GetFile 时决定是否影响结果。
		if errors.Is(err, os.ErrNotExist) {
			continue
		}
		if err != nil {
			return set, fmt.Errorf("打开产物 %s: %w", name, err)
		}
		if size < 0 || size > launcher.MaxArtifactBytes-total {
			return set, errors.Join(fmt.Errorf("产物总量超限: %s", name), file.Close())
		}
		total += size
		set.outputs = append(set.outputs, Output{Path: name, SizeBytes: size})
		set.files = append(set.files, ownFile(file))
	}
	return set, nil
}
func (s *artifactSet) Close() error {
	if s == nil {
		return nil
	}
	if !s.closed {
		s.closed = true
		s.closeErr = closeFiles(s.files...)
		s.files = nil
	}
	return s.closeErr
}

// executionResult 接管已结束执行的产物；Result 自身只含可序列化事实。
// 交付者必须成功关闭产物后才能发送 Completion。
type executionResult struct {
	Result
	artifacts *artifactSet
}

func (r *executionResult) Close() error { return r.artifacts.Close() }

// CopyN 将短文件视为交付失败，避免下一帧被当成剩余文件内容。
func (r *executionResult) WriteFiles(w io.Writer) error {
	var files []*ownedFile
	if r.artifacts != nil {
		files = r.artifacts.files
	}
	if len(files) != len(r.Outputs) {
		return fmt.Errorf("产物句柄与元数据不一致")
	}
	for i, file := range files {
		if _, err := io.CopyN(w, file, r.Outputs[i].SizeBytes); err != nil {
			return fmt.Errorf("交付产物 %s: %w", r.Outputs[i].Path, err)
		}
	}
	return nil
}
