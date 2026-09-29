package preflight

import (
	"context"
	"fmt"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/config"
)

// Sandbox 是自检所消费的能力。接口由消费方定义，实现是 judge 已有的 sandbox 客户端；
// Version 必须拒绝重定向并限制响应大小，否则可能把别的地址当成本机的 sandbox。
type Sandbox interface {
	Version(ctx context.Context) (contract.SandboxVersion, error)
}

// Check 在节点注册前运行一次，任何一项不满足都拒绝上线。
//
// sandbox 报告 linux 隔离与配置了部署清单必须同时成立：只有一边成立，说明部署与配置不一致，
// 这时既不能按原生部署核对，也不能当作开发环境放行。
func Check(ctx context.Context, j config.Settings, sandbox Sandbox) error {
	version, err := sandbox.Version(ctx)
	if err != nil {
		return fmt.Errorf("sandbox unavailable: %w", err)
	}
	if version.Name != "cherry-oj-sandbox" || version.Version == "" || len(version.Version) > 128 {
		return fmt.Errorf("sandbox version invalid")
	}
	if version.Isolation != "linux" && j.Node.DeploymentManifest == "" {
		return nil
	}
	if version.Isolation != "linux" || j.Node.DeploymentManifest == "" {
		return fmt.Errorf("Linux sandbox requires matching deployment manifest and isolation")
	}
	return checkDeployment(ctx, j)
}
