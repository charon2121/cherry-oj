package store

import (
	"io"
)

// Store 保存执行输入与产物引用；它不拥有 Container，也不保证文件会一直保留。
type Store interface {
	// Put 同步消费 r；只有完整保存后才发布 ref，r 的关闭仍由调用者负责。
	Put(r io.Reader) (string, error)

	// Get 按 ref 打开一个流；ref 不存在返回 ErrNotFound
	// 返回的 ReadCloser 由调用方负责 Close
	Get(ref string) (io.ReadCloser, error)
	// Delete 使 ref 对新读取失效；已打开的 reader 仍需由调用者关闭后才能释放占用。
	Delete(ref string) error
}
