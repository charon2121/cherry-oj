// Package node 把节点的四件事装配到一起：身份、注册心跳、数据安装与环境探测。
//
// 这四件事此前挤在同一个包里。它们的生命周期、对端和失败后果都不同：
// 身份在进程启动时算一次就不再变；注册心跳是对控制面的长期客户端；安装是收数据落盘的服务端；
// 探测只在启动时跑一次。拆开之后，每一件各自决定自己的边界，本文件只负责把它们接起来。
package node

import (
	"context"
	"log/slog"
	"net/http"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/config"
	"cherry-oj/judge-engine/judge/internal/node/identity"
	"cherry-oj/judge-engine/judge/internal/node/install"
	"cherry-oj/judge-engine/judge/internal/node/probe"
	"cherry-oj/judge-engine/judge/internal/node/registry"
)

// Environment 是探测得到的执行环境事实。
type Environment = identity.Environment

// DeclaredEnvironment 取配置中声明的环境，供未启用节点链路时使用。
func DeclaredEnvironment(s config.Settings) Environment { return identity.Declared(s) }

// Sandbox 是环境探测所消费的能力，由 judge 现有的 sandbox 客户端实现。
type Sandbox = probe.Sandbox

// ProbeEnvironment 通过 sandbox 已有的有界接口读取真实执行环境。
// 配置里声明的那份只是意图，不能替代实测。
func ProbeEnvironment(ctx context.Context, s config.Settings, sandbox Sandbox) (Environment, error) {
	return probe.Environment(ctx, s, sandbox)
}

type Node struct {
	identity  identity.Identity
	registry  *registry.Registry
	installer *install.Installer
}

// New 接收配置与已探明的执行环境两个值：身份由两者共同决定，
// 不再由配置结构兼任探测结果的容器。
func New(j config.Settings, env Environment, logger *slog.Logger) (*Node, error) {
	if err := j.Node.Validate(); err != nil {
		return nil, err
	}
	if logger == nil {
		logger = slog.Default()
	}
	id, err := identity.New(j, env)
	if err != nil {
		return nil, err
	}
	registration := id.Registration()
	installer, err := install.Open(j.Node, j.TestdataRoot, registration, logger)
	if err != nil {
		return nil, err
	}
	return &Node{identity: id, installer: installer,
		registry: registry.New(j.Node, registration, logger)}, nil
}

func (n *Node) Close() error { return n.installer.Close() }

// Registration 返回本节点这次进程的身份副本。
func (n *Node) Registration() contract.NodeRegistration { return n.identity.Registration() }

// Run 随进程 context 退出；控制面不可用只延迟注册，不关闭 Judge 健康入口。
func (n *Node) Run(ctx context.Context) { n.registry.Run(ctx) }

// Handler 在 Judge 的 HTTP 入口上挂载节点安装端点，其余路由回落给 fallback。
func (n *Node) Handler(fallback http.Handler) http.Handler { return n.installer.Handler(fallback) }
