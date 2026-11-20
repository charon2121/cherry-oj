// Package preflight 是节点注册前的启动自检：进程内执行层报告的隔离后端与部署一致，
// 原生 Linux 部署下发布文件和 root 管理的部署清单一致。通过自检的语言交给 judge 逐门运行冒烟。
package preflight
