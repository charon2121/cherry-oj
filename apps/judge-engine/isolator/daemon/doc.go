//go:build linux && amd64

// Package daemon 是 P3 的常驻服务：以 root 运行，监听只有 sandbox 能连的 Unix socket，
// 每条连接占一个槽位、执行一次命令、交付结果后关闭。
//
// 启动（Serve）：校验安装与 rootfs 清单 → 取状态目录的独占锁 → 回收上次遗留的 cgroup
// 与挂载点 → 每个槽位用真实执行链跑一次冒烟 → 创建 socket（root:服务组 0660）。
// 任何一步失败都不开放 socket。
//
// 每条连接（serveConn）：SO_PEERCRED 认证 → 取槽位（没有空槽位就直接关闭，不排队，
// 排队在 sandbox）→ 5 s 内读完请求帧 → execution.Run → 写 Result、产物、Completion →
// 归还槽位 → 关闭连接。客户端读到正常 EOF 才能确认槽位已归还。
// 回收无法确认时 Serve 停止接单并返回错误，交给 systemd 处理；命令本身的失败只写进 Result。
//
// 配置来自 root 管理的 JSON 文件，字段见 apps/judge-engine/isolator.example.json；其中的摘要、
// 账号与 cgroup 路径必须来自实际部署，示例本身不能直接启动。
//
// 文件：server（监听与连接）、slots（槽位与每槽身份）、config、installation（安装自检与冒烟）、
// recovery（启动时回收遗留资源）、rootfs（rootfs 清单核验）、ownership（root 所有权路径检查）。
package daemon
