#!/bin/sh
#
# 启用仓库自带的 git hooks。**每个新克隆都要跑一次。**
#
# 为什么不能自动生效：.git/hooks/ 不进版本库，而 git 也不会自动信任仓库里的
# 可执行脚本——否则 clone 一个陌生仓库就等于给了它任意代码执行权限。
# core.hooksPath 把 hook 目录指到仓库内被跟踪的 .githooks/，一行配置换来
# 「hook 能随代码一起演进」，代价就是这一次手动执行。

set -eu

root=$(git rev-parse --show-toplevel)
cd "$root"

git config core.hooksPath .githooks
chmod +x .githooks/* 2>/dev/null || true

echo "✓ hooks 已启用（core.hooksPath = .githooks）"
echo
echo "  pre-commit   gofmt 检查 + go vet + contracts JSON 语法   ~0.4s"
echo "  commit-msg   Conventional Commits 标题格式"
echo "  pre-push     go test -race                                ~6s"
echo
echo "  临时跳过：git commit --no-verify / git push --no-verify"
echo "  停用：git config --unset core.hooksPath"
