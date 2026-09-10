#!/bin/sh
# Same config loading as judge-environment, with a fixed read-only query program.
set -eu
task_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "$task_dir/../../../.." && pwd)
server_dir="$repo_dir/apps/server"
jar_path="$server_dir/judging-service/target/judging-service-0.0.1-SNAPSHOT.jar"
evidence_tmp=$(mktemp -d "${TMPDIR:-/tmp}/cherry-work048-evidence.XXXXXX")
trap 'rm -rf "$evidence_tmp"' EXIT HUP INT TERM
unzip -q "$jar_path" 'BOOT-INF/*' -d "$evidence_tmp"
java_bin=java
if [ -n "${JAVA_HOME:-}" ]; then java_bin="$JAVA_HOME/bin/java"; fi
cd "$server_dir"
"$java_bin" -Xmx128m -cp "$server_dir/judging-service/src/main/resources:$evidence_tmp/BOOT-INF/classes:$evidence_tmp/BOOT-INF/lib/*" \
  "$task_dir/ReadEvidence.java"
