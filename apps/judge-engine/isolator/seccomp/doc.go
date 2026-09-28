//go:build linux && amd64

// Package seccomp 构造固定版本的 seccomp BPF 过滤器，请求不能扩展规则。
//
// 三种策略：Supervisor 给 P4 在放行前装在自己身上；Command 与 Toolchain 给 P5 在
// execve 前装上，编译器（g++/gcc）用 Toolchain，其他命令用 Command。
package seccomp
