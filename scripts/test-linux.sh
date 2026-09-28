#!/bin/sh
#
# 在 macOS 上用 linux/amd64 容器跑 judge-engine 的 Go 测试。
#
# 为什么需要它：isolator 只在 linux/amd64 编译，本机 `go test ./...` 会整棵跳过它；
# pre-commit 的交叉 vet 只能做类型检查，跑不了测试。
#
# 用法：
#   scripts/test-linux.sh                          # 默认 -race -count=1 ./...
#   scripts/test-linux.sh -count=1 ./isolator/...  # 参数原样交给 go test
#
# 已知限制（以 CI 的真实 Linux 为准）：
#   - Docker Desktop 的 amd64 模拟不实现 openat2，isolator/execution 的
#     TestOutputRejectsLinksAndSpecialFiles 在这里必然报 "function not implemented"。
#   - 需要 root 与委派 cgroup 的边界测试在 isolator/tests/boundary，由 CI 单独构建运行，
#     容器里的普通 go test 覆盖不到真实隔离。
#
# 仓库根目录整个挂进容器：contract 测试要读仓库根下的 contracts/。
# 模块与构建缓存放在具名卷里，第二次起不必重新下载。

set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
go_version=$(sed -n 's/^go //p' "$root/apps/judge-engine/go.mod")

if ! docker info >/dev/null 2>&1; then
    echo "✗ 需要可用的 Docker（Docker Desktop 或兼容实现）" >&2
    exit 1
fi

[ $# -eq 0 ] && set -- -race -count=1 ./...

exec docker run --rm --platform linux/amd64 \
    -v "$root":/repo \
    -v cherry-gomod:/go/pkg/mod \
    -v cherry-gocache:/root/.cache/go-build \
    -w /repo/apps/judge-engine \
    "golang:${go_version}-bookworm" \
    go test "$@"
