#!/bin/sh
# Local pure-Go binaries; rootfs is assembled separately from the reviewed lock.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
output=${1:?provide a new absolute output directory}
case "$output" in /*) ;; *) echo 'output must be absolute' >&2; exit 2;; esac
mkdir "$output"
mkdir "$output/bin"
cd "$root/apps/judge-engine"
export CGO_ENABLED=0 GOOS=linux GOARCH=amd64
for name in sandbox-helper sandbox judge; do
  go build -o "$output/bin/$name" "./cmd/$name"
done
cp "$root/deploy/sandbox-linux/rootfs/ubuntu24-amd64-smoke.lock.json" "$output/packages.lock.json"
echo 'Built binaries and copied package lock. Add verified rootfs/ and manifest.json before installation.'
