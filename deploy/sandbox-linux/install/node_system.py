"""调用原生节点的系统命令与 systemd 服务。"""

from __future__ import annotations

import subprocess
from pathlib import Path

import layout


def run(*args: str) -> str:
    return subprocess.run(args, check=True, text=True, capture_output=True, timeout=90).stdout.strip()


def systemctl(*args: str) -> str:
    return run('systemctl', *args)


def has_unit_conflict(unit: str) -> bool:
    properties = systemctl(
        'show',
        unit,
        '-p',
        'LoadState',
        '-p',
        'ActiveState',
        '-p',
        'Transient',
        '-p',
        'FragmentPath',
        '-p',
        'DropInPaths',
        '-p',
        'ControlGroup',
    )
    return unit_conflicts(unit, dict(line.split('=', 1) for line in properties.splitlines()))


def verify_units() -> None:
    run('systemd-analyze', 'verify', *(str(layout.SYSTEMD / name) for name in layout.UNITS))


def unit_conflicts(unit: str, facts: dict[str, str]) -> bool:
    if facts.get('LoadState') == 'not-found':
        return False
    # systemd synthesizes inactive slice objects even when no unit file exists.
    implicit_slice = (
        unit.endswith('.slice')
        and facts.get('LoadState') == 'loaded'
        and facts.get('ActiveState') == 'inactive'
        and facts.get('Transient') == 'no'
        and all(facts.get(key) == '' for key in ('FragmentPath', 'DropInPaths', 'ControlGroup'))
    )
    return not implicit_slice


def start_services() -> None:
    try:
        systemctl('start', layout.JUDGE_UNIT)
        run('python3', str(layout.ETC / 'health.py'), 'judge')
    except Exception:
        systemctl('stop', *reversed(layout.UNITS[1:]))
        raise


def stop_services() -> None:
    systemctl('stop', *reversed(layout.UNITS[1:]))
    for name in layout.UNITS[1:]:
        if (Path(layout.GROUP) / name).exists():
            raise ValueError('service cgroup remains after stop: ' + name)
