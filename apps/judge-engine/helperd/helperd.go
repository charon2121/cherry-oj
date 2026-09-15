package helperd

import (
	"context"

	"cherry-oj/judge-engine/helperd/internal/helper"
	"cherry-oj/judge-engine/helperd/internal/launcher"
)

// Config 是 root 管理的本机配置；客户端请求不能选择路径、身份或并发槽位。
type Config = helper.Config

// LoadConfig 读取并校验 root 管理的配置文件。
func LoadConfig(path string) (Config, error) { return helper.LoadConfig(path) }

// Dispatch 必须在读取配置、启动服务或建立任何 goroutine 之前调用。
// 隐藏的 init/exec 角色是本二进制重新启动后的不同进程，只消费已继承的匿名 FD；
// 返回 true 表示当前进程是其中一个角色，调用方应立即退出主流程。
func Dispatch() bool { return launcher.Dispatch() }

// Run 启动特权 helper 并在 ctx 取消后收尾。
// 调用者必须用 systemd 托管：SIGKILL 兜底不能依赖 Go 的 defer。
func Run(ctx context.Context, c Config) error { return helper.Serve(ctx, c) }
