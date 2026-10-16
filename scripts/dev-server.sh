#!/usr/bin/env bash
# 按 ports.env 的端口启动一个 Java 服务：scripts/dev-server.sh gateway-service
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
# ports.env 给出缺省值；shell 里已经设置的同名变量优先。
while IFS='=' read -r key value; do
  if [[ $key == CHERRY_*_PORT ]]; then export "$key=${!key:-$value}"; fi
done < "$root/ports.env"
cd "$root/apps/server"
exec ./mvnw -pl "${1:?用法: dev-server.sh <模块名>}" spring-boot:run
