package preflight

import (
	"context"
	"fmt"
	"runtime"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/config"
)

// VersionReader 读取节点自检所需的执行层版本与隔离类型。
type VersionReader interface {
	Version(ctx context.Context) (contract.SandboxVersion, error)
}

// Check 在节点注册前核对执行层与部署，返回需要启动冒烟的语言。
// 原生部署以已核验清单登记的工具链为准，开发环境使用配置的语言。
//
// 执行层报告 linux 隔离与配置了部署清单必须同时成立：只有一边成立，说明部署与配置不一致，
// 这时既不能按原生部署核对，也不能当作开发环境放行。
func Check(ctx context.Context, j config.Settings, versions VersionReader) ([]string, error) {
	version, err := versions.Version(ctx)
	if err != nil {
		return nil, fmt.Errorf("sandbox unavailable: %w", err)
	}
	if version.Name != "cherry-oj-sandbox" || version.Version == "" || len(version.Version) > 128 {
		return nil, fmt.Errorf("sandbox version invalid")
	}
	if version.Isolation != "linux" && j.Node.DeploymentManifest == "" {
		return validLanguages(j.Node.Languages)
	}
	if version.Isolation != "linux" || j.Node.DeploymentManifest == "" {
		return nil, fmt.Errorf("Linux sandbox requires matching deployment manifest and isolation")
	}
	if runtime.GOOS != "linux" || runtime.GOARCH != "amd64" {
		return nil, fmt.Errorf("native deployment requires Linux/amd64")
	}
	languages, err := verifyDeployment(ctx, j.Node.DeploymentManifest)
	if err != nil {
		return nil, err
	}
	return validLanguages(languages)
}
