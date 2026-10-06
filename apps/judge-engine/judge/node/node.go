package node

import (
	"context"
	"log/slog"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/node/identity"
	"cherry-oj/judge-engine/judge/node/preflight"
	"cherry-oj/judge-engine/judge/node/registry"
)

// Sandbox 是启动自检所消费的能力，由 judge 现有的 sandbox 客户端实现。
type Sandbox = preflight.Sandbox

// Preflight 在注册前自检：对端是 cherry-oj 的 sandbox，原生部署与部署清单一致。
func Preflight(ctx context.Context, s config.Settings, sandbox Sandbox) error {
	return preflight.Check(ctx, s, sandbox)
}

type Node struct {
	identity identity.Identity
	registry *registry.Registry
}

// New 由配置生成本次进程的身份，并准备好注册心跳。
func New(j config.Settings, logger *slog.Logger) (*Node, error) {
	if err := j.Node.Validate(); err != nil {
		return nil, err
	}
	if logger == nil {
		logger = slog.Default()
	}
	id, err := identity.New(j)
	if err != nil {
		return nil, err
	}
	registration := id.Registration()
	return &Node{identity: id, registry: registry.New(j.Node, registration, logger)}, nil
}

// Registration 返回本节点这次进程的身份副本。
func (n *Node) Registration() contract.NodeRegistration { return n.identity.Registration() }

// Run 随进程 context 退出；控制面不可用只延迟注册，不关闭 Judge 健康入口。
func (n *Node) Run(ctx context.Context) { n.registry.Run(ctx) }
