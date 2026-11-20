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

// Preflight 在注册前核对进程内执行层的版本、隔离类型和原生部署清单。
func Preflight(ctx context.Context, s config.Settings, versions preflight.VersionReader) error {
	return preflight.Check(ctx, s, versions)
}

// Languages 返回本节点要声明的语言：原生部署取发布包锁，开发环境取配置；在 Preflight 之后调用。
func Languages(s config.Settings) ([]string, error) {
	return preflight.Languages(s)
}

type Node struct {
	identity identity.Identity
	registry *registry.Registry
}

// New 由配置与实测通过的语言生成本次进程的身份，并准备好注册心跳。
func New(j config.Settings, languages []string, logger *slog.Logger) (*Node, error) {
	if err := j.Node.Validate(); err != nil {
		return nil, err
	}
	if logger == nil {
		logger = slog.Default()
	}
	id, err := identity.New(j, languages)
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
