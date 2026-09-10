#!/bin/sh
# 私有 apt 配置不加载宿主 apt hooks；只更新测试索引并下载，不安装软件。
set -eu
base=$1
case "$base" in /var/lib/cherry-sandbox-test/work048-*) ;; *) exit 2;; esac
mkdir -p "$base/debs" "$base/apt-cache" "$base/apt-log" "$base/apt-lists"
: > "$base/empty-status"
cat > "$base/apt.conf" <<CONFIG
Dir::Etc::parts "-";
Dir::Etc::main "-";
Dir::State::status "$base/empty-status";
Dir::State::lists "$base/apt-lists";
Dir::Cache "$base/apt-cache";
Dir::Cache::archives "$base/debs";
Dir::Log "$base/apt-log";
APT::Install-Recommends "false";
Debug::NoLocking "true";
CONFIG
sha256sum /var/lib/dpkg/status > "$base/host-status.before"
export APT_CONFIG="$base/apt.conf"
apt-get -q update > "$base/apt-update.log" 2>&1
apt-get -q --yes --download-only install g++ coreutils > "$base/download.log" 2>&1
sha256sum -c "$base/host-status.before"
tail -6 "$base/download.log"
