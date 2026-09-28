//go:build linux && amd64

// Package initproc 是 P4：每次执行新建，在新 PID namespace 里当 PID 1 的可信 init。
//
// Run 从继承的 FD 读 StageSpec 与输入，搭好只读 rootfs 与 /work 工作区，把工作区目录 FD
// 交给 P3，再启动 P5 并在握手中转发 GO（顺序见 startup 的包文档）。用户程序结束后上报
// exit 事件，然后继续回收孤儿进程，直到 P3 停掉整个 cgroup。
//
// P4 在放行前把自己降到 init 身份并装 Supervisor 策略，此后不再持有 root 权限。
// P3 消失时存活管道读到 EOF，P4 立即退出，整个 namespace 随之消失。
//
// 文件：init（Run 与会话、上报退出、回收孤儿）、rootfs（挂载与写入输入）、
// payload（解析命令、启动 P5、握手）。
package initproc
