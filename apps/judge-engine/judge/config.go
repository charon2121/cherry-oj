package judge

import judgeconfig "cherry-oj/judge-engine/judge/internal/config"

// Config 是 judge 服务的运行配置。真正的字段定义在 judge/internal/config：
// flow 与 node 也要读它，而它们不能引用本包（那会构成循环引用）。
type Config = judgeconfig.Config

// DefaultConfig 返回有界默认配置。
func DefaultConfig() Config { return judgeconfig.Default() }

// LoadConfig 按「默认值 → YAML → 环境变量」装配 judge 配置并校验。
func LoadConfig(path string) (Config, error) { return judgeconfig.Load(path) }
