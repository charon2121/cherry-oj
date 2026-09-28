// Package store 按 ref 保存执行输入与产物：Put 完整写入后才发布一个 32 位十六进制 ref，
// Get 打开读取，Delete 使 ref 对新读取失效。它不参与执行，也不保证文件会一直保留：
// 超过保留期的文件由服务定时 Sweep 删除；总量或条目数达到上限时，新的 Put 返回
// ErrCapacity，不会挤掉旧文件。
package store
