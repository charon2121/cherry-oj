#!/usr/bin/env bash
# 按 ports.env 的端口启动一个 Java 服务：scripts/dev-server.sh gateway-service
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
# ports.env 给出缺省值；shell 里已经设置的同名变量优先。
while IFS='=' read -r key value; do
  if [[ $key == CHERRY_*_PORT ]]; then export "$key=${!key:-$value}"; fi
done < "$root/ports.env"
# 服务身份密钥：每个服务一把私钥，公钥目录在 public/（docs/protocols/service-identity.md）。
# 首次运行时生成到被忽略的 .local/service-identity/，之后复用；缺的密钥会补上。
export CHERRY_SERVICE_IDENTITY_DIR="${CHERRY_SERVICE_IDENTITY_DIR:-$root/.local/service-identity}"
"$root/scripts/service-identity-keys.sh" "$CHERRY_SERVICE_IDENTITY_DIR"
cd "$root/apps/server"
exec ./mvnw -pl "${1:?用法: dev-server.sh <模块名>}" spring-boot:run
