package store

import (
	"io"
)

type Store interface {
	// Put 读完 r 并存下，返回 ref
	Put(r io.Reader) (string, error)

	// Get 按 ref 打开一个流；ref 不存在返回 ErrNotFound
	// 返回的 ReadCloser 由调用方负责 Close
	Get(ref string) (io.ReadCloser, error)
	Delete(ref string) error
}
