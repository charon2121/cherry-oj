package testcase

import (
	"context"
	"errors"
	"fmt"
	"io/fs"
	"syscall"
	"testing"
)

// 白盒：重试条件是协议的一部分（只重试一次，只在像是撞上了写入方替换数据时重试），分类本身值得单独钉住。
func TestRetryableClassifiesWhatLooksLikeARaceWithTheWriter(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want bool
	}{
		{"文件与元数据对不上", fmt.Errorf("%w: 1.out has sha256 x", errChanged), true},
		{"文件不见了", fmt.Errorf("open 1.in: %w", fs.ErrNotExist), true},
		{"本地读取时操作系统报错", &fs.PathError{Op: "open", Path: "/data/p/1.in", Err: syscall.EINVAL}, true},
		{"大小上限", errors.New("test data is 9 bytes, over the 8 byte limit"), false},
		{"HTTP 状态码", errors.New("GET http://h/p/1.in: unexpected status 500"), false},
		{"上下文取消", context.Canceled, false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := retryable(tt.err); got != tt.want {
				t.Errorf("retryable(%v) = %v, want %v", tt.err, got, tt.want)
			}
		})
	}
}
