#!/usr/bin/env bash
# 按 ports.env 的端口用 Compose 启动 judge，让它向 judging-service 注册：scripts/dev-judge.sh
# 额外参数原样交给 docker compose，例如 scripts/dev-judge.sh down、scripts/dev-judge.sh logs -f judge。
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
# ports.env 给出缺省值；shell 里已经设置的同名变量优先。
while IFS='=' read -r key value; do
  if [[ $key == CHERRY_*_PORT ]]; then export "$key=${!key:-$value}"; fi
done < "$root/ports.env"
: "${CHERRY_TEST_DATA_ROOT:?先设置 CHERRY_TEST_DATA_ROOT（problem-service 写测试数据的绝对路径）}"
cd "$root"
if [[ $# -eq 0 ]]; then set -- up -d --build --wait; fi
exec docker compose "$@"
