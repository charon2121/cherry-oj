// Package config 定义 judge 服务的运行配置。
//
// 它只描述 judge 自己需要的东西：sandbox 段配错不会让 judge 拒绝启动，反之亦然。
// 装配机制（默认值 → YAML → 环境变量 → 校验）在 internal/platform/config。
package config
