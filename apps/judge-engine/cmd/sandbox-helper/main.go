// sandbox-helper 只能由本机服务管理器启动；配置由 root 管理，不接受远端特权参数。
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"cherry-oj/judge-engine/helperd"
)

func main() {
	// re-exec 子进程只消费继承 FD；必须在服务配置、信号监听及其他 goroutine 建立前分流，
	// 避免 init/exec 角色误入特权服务循环。
	if helperd.Dispatch() {
		return
	}
	path := flag.String("config", "", "root 管理的 helper JSON 配置绝对路径")
	flag.Parse()
	if *path == "" {
		fmt.Fprintln(os.Stderr, "必须提供 --config")
		os.Exit(2)
	}
	c, err := helperd.LoadConfig(*path)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()
	if err = helperd.Run(ctx, c); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
