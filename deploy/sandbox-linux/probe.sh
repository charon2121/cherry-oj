#!/bin/sh
# TASK-093: inventory only. No files, cgroups, namespaces or services are created.
# Exit 0 means inventory completed, never that isolation is supported.
set -u
export LC_ALL=C
export SYSTEMD_PAGER=cat
export SYSTEMD_COLORS=0

value() { printf '%s=%s\n' "$1" "$2"; }
read_value() {
    if [ -r "$2" ]; then
        printf '%s=' "$1"
        tr '\n' ' ' < "$2"
        printf '\n'
    elif [ -e "$2" ]; then
        value "$1" permission-denied
    else
        value "$1" absent-or-inaccessible
    fi
}

value probe_version 1
value observed_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
value os "$(uname -s)"
if [ "$(uname -s)" != Linux ]; then
    value result unsupported-os
    exit 2
fi
value kernel "$(uname -r)"
value architecture "$(uname -m)"
# Read only these public fields; do not source arbitrary shell configuration.
awk -F= '/^(ID|VERSION_ID|PRETTY_NAME)=/ {print "distribution." $0}' /etc/os-release
value uid "$(id -u)"
value gid "$(id -g)"
value logical_cpus "$(getconf _NPROCESSORS_ONLN)"
awk '/^(MemTotal|MemAvailable|SwapTotal|SwapFree):/ {print "resource." $1 " " $2 " " $3}' /proc/meminfo
awk '{print "load.1m=" $1; print "load.5m=" $2; print "load.15m=" $3}' /proc/loadavg
awk '/^(CapInh|CapPrm|CapEff|CapBnd|CapAmb|NoNewPrivs|Seccomp|Seccomp_filters):/ {print "caller." $0}' /proc/self/status
for ns in mnt pid net ipc uts user cgroup; do
    if [ -e "/proc/self/ns/$ns" ]; then value "namespace.$ns" present; else value "namespace.$ns" absent-or-inaccessible; fi
done
read_value lsm /sys/kernel/security/lsm
read_value apparmor.enabled /sys/module/apparmor/parameters/enabled
for setting in kernel/unprivileged_userns_clone kernel/apparmor_restrict_unprivileged_userns user/max_user_namespaces; do
    read_value "sysctl.$setting" "/proc/sys/$setting"
done
value cgroup_filesystem "$(stat -f -c %T /sys/fs/cgroup 2>/dev/null || printf unknown)"
for group in /sys/fs/cgroup /sys/fs/cgroup/system.slice /sys/fs/cgroup/user.slice; do
    value cgroup.inspect "$group"
    for item in cgroup.controllers cgroup.subtree_control cgroup.type; do
        read_value "$item" "$group/$item"
    done
    for item in memory.peak cgroup.kill cpu.stat cpu.max memory.max memory.swap.max memory.oom.group memory.events pids.max cgroup.events; do
        if [ -e "$group/$item" ]; then value "$item" present; else value "$item" absent-or-inaccessible; fi
    done
done
if [ -r "/boot/config-$(uname -r)" ]; then
    awk '/^CONFIG_(NAMESPACES|UTS_NS|IPC_NS|USER_NS|PID_NS|NET_NS|CGROUPS|CGROUP_PIDS|MEMCG|SECCOMP|SECCOMP_FILTER)=/ {print "kernel_config." $0}' "/boot/config-$(uname -r)"
else
    value kernel_config unavailable
fi
for program in systemctl systemd-detect-virt cc gcc g++ make pkg-config runc crun nsjail isolate; do
    if command -v "$program" >/dev/null 2>&1; then value "tool.$program" present; else value "tool.$program" absent; fi
done
if command -v systemd-detect-virt >/dev/null 2>&1; then
    value virtualization "$(systemd-detect-virt 2>/dev/null || printf unknown)"
fi
if command -v systemctl >/dev/null 2>&1; then
    systemctl --version | head -n 1
    # Service names only: no command lines, environment, journals or credentials.
    value services begin
    systemctl list-units --type=service --state=running --no-legend --no-pager 2>/dev/null | awk '{print $1}'
    value services end
    value project_units begin
    systemctl list-unit-files 'cherry-sandbox*' --no-legend --no-pager 2>/dev/null
    value project_units end
    value caller_delegation begin
    systemctl show "session-${XDG_SESSION_ID:-unknown}.scope" --property=LoadState --property=Delegate --property=DelegateControllers --no-pager 2>/dev/null || value caller_delegation unavailable
    value caller_delegation end
fi
# Aggregate process names avoid leaking command arguments or host/user names.
value process_names begin
ps -eo comm= | sort | uniq -c
value process_names end
value behavior_tests not-run
value dedicated_host unconfirmed
value result inventory-only
