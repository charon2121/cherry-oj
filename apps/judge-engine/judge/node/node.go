package node

import (
	"context"
	"log/slog"

	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/node/identity"
	"cherry-oj/judge-engine/judge/node/registry"
)

type Node struct {
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
	return &Node{registry: registry.New(j.Node, id.Registration(), logger)}, nil
}

// Run 随进程 context 退出；控制面不可用只延迟注册，不关闭 Judge 健康入口。
func (n *Node) Run(ctx context.Context) { n.registry.Run(ctx) }
