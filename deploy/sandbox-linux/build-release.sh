#!/bin/sh
# Go 服务为纯 Go 静态构建；隔离执行器是 C 程序（需要 gcc 与 libseccomp-dev，libseccomp 静态链接）。
# 各语言的工具链（toolchains/<语言>/）由锁定的软件包另行组装：rootfs/toolchains.py build。
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
output=${1:?provide a new absolute output directory}
case "$output" in /*) ;; *) echo 'output must be absolute' >&2; exit 2;; esac
mkdir "$output"
mkdir "$output/bin" "$output/libexec"
cd "$root/apps/judge-engine"
export CGO_ENABLED=0 GOOS=linux GOARCH=amd64
# judge 在本进程内装配执行层，节点上只有这一个 Go 服务。
go build -o "$output/bin/judge" ./cmd/judge
# 执行器在独立目录构建，不在源码树里留下产物；安装时由 manage.py 设为 setuid-root。
make -s -C "$root/apps/sandbox" BUILD="$output/executor-build" OUT="$output/libexec/sandbox"
rm -rf "$output/executor-build"
echo 'Built binaries. Add toolchains/ with rootfs/toolchains.py build before installation.'
