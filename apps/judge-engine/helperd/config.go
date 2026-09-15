package helperd

import "cherry-oj/judge-engine/helperd/internal/helper"

// Config 是 root 管理的本机配置；客户端请求不能选择路径、身份或并发槽位。
//
// 它不走 judge/sandbox 那套「YAML + 环境变量」装配：特权配置必须来自 root 管理的
// 文件，路径与祖先目录的所有权都要核验，不能让环境变量参与决定身份与并发槽位。
type Config = helper.Config

// LoadConfig 读取并校验 root 管理的配置文件。
func LoadConfig(path string) (Config, error) { return helper.LoadConfig(path) }
