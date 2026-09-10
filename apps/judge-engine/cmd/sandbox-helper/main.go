// sandbox-helper 只能由本机服务管理器启动；配置由 root 管理，不接受远端特权参数。
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"cherry-oj/judge-engine/internal/sandbox/helper"
	"cherry-oj/judge-engine/internal/sandbox/launcher"
)

func main() {
	if launcher.Dispatch() {
		return
	}
	path := flag.String("config", "", "root 管理的 helper JSON 配置绝对路径")
	flag.Parse()
	if *path == "" {
		fmt.Fprintln(os.Stderr, "必须提供 --config")
		os.Exit(2)
	}
	c, err := helper.LoadConfig(*path)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()
	if err = helper.Serve(ctx, c); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
