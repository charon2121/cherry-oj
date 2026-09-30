// Package preflight 是节点注册前的启动自检：进程内执行层报告的隔离后端与部署一致，
// 原生 Linux 部署下发布文件与资源上界和 root 管理的部署清单一致。
//
// 自检只回答「能不能上线」，不产出任何上报给控制面的机器信息——节点身份见 identity。
package preflight
