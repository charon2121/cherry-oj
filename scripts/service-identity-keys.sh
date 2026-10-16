#!/usr/bin/env bash
# 生成服务身份密钥：scripts/service-identity-keys.sh <目录>
# 每个要调用其他服务的服务一对 RSA 密钥：<name>-private.pem（只给该服务）与 <name>-public.pem（给 user-service）。
# 已存在的不覆盖，可重复运行；并发运行时用目录锁串行化，保证同一服务的私钥与公钥来自同一次生成。
# 容器以 10001:10001 运行（apps/server/Dockerfile）：Linux 上挂载给容器时设 SERVICE_IDENTITY_OWNER=10001:10001，
# 私钥会改为该属主（仍是 600），否则容器读不到。
set -euo pipefail
dir="${1:?用法: service-identity-keys.sh <目录>}"
mkdir -p "$dir"
chmod 700 "$dir"
lock="$dir/.generating"
acquired=0
for _ in $(seq 100); do
  if mkdir "$lock" 2>/dev/null; then acquired=1; break; fi
  sleep 0.1
done
(( acquired )) || { echo "service-identity-keys: 等不到 ${lock}，若没有其他生成进程请删除它" >&2; exit 1; }
trap 'rmdir "$lock"' EXIT
for service in gateway-service submission-service judging-service; do
  private_key="$dir/$service-private.pem"
  public_key="$dir/$service-public.pem"
  if [[ ! -f $private_key ]]; then
    (umask 077 && openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$private_key" 2>/dev/null)
    openssl pkey -in "$private_key" -pubout -out "$public_key"
    echo "service-identity-keys: 已生成 ${service} 的服务身份密钥（${dir}）" >&2
  elif [[ ! -f $public_key ]]; then
    openssl pkey -in "$private_key" -pubout -out "$public_key"
  fi
  if [[ -n ${SERVICE_IDENTITY_OWNER:-} ]]; then
    chown "$SERVICE_IDENTITY_OWNER" "$private_key" "$public_key"
  fi
done
