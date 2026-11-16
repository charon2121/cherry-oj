#!/usr/bin/env python3
"""应用部署工具测试使用的最小发布与本机替身。"""

from __future__ import annotations

import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from artifacts import DATABASES, JARS, OPS, SERVICES, Manifest, artifact_digest, digest
from host import Host, Mysql
from release import package
from state import Store, save


PORTS = dict(zip(SERVICES, (8081, 8082, 8083, 18084, 8080)))
MYSQL = Mysql('fixture-mysql', '/run/client.cnf', 'fixture_')
NODE = dict(release='fixture-node', mainPid=123, activeState='active')


def contents(root: Path, label: str = 'old', migration: str = 'schema') -> dict[str, Path]:
    root.mkdir()
    result = {}
    for name in JARS:
        path = root / name
        path.parent.mkdir(exist_ok=True)
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('BOOT-INF/classes/db/migration/V1__fixture.sql', migration)
            archive.writestr('BOOT-INF/classes/fixture.txt', label)
        result[name] = path
    for name, text in [('web/index.html', label), (OPS, '#!/bin/sh\nexit 0\n')]:
        path = root / name
        path.parent.mkdir(exist_ok=True)
        path.write_text(text)
        result[name] = path
    return result


def legacy(root: Path, label: str = 'old', migration: str = 'schema') -> Manifest:
    inputs = contents(root, label, migration)
    files = {name: digest(path) for name, path in inputs.items()}
    document = dict(sourceSha=None, files=files, artifactSetSha256=artifact_digest(files))
    save(root / 'release.json', document, 0o644)
    return Manifest.parse(document, legacy=True)


def installation(root: Path) -> Store:
    home = root / 'home'
    (home / 'releases').mkdir(parents=True)
    manifest = legacy(home / 'releases/old')
    (home / 'current').symlink_to('releases/old')
    save(home / 'deployment.json', dict(sourceSha=None, judgeRelease='obsolete-node', release='old', previous=None,
                                       artifactSetSha256=manifest.artifact_sha256, ports=PORTS,
                                       services=['cherry-app-' + name + '.service' for name in SERVICES],
                                       webListen='127.0.0.1:18080'), 0o644)
    return Store(home, trusted=False)


def bundle(root: Path) -> tuple[Path, str, str]:
    inputs = contents(root / 'inputs', 'new')
    output = root / 'package'
    output.mkdir()
    metadata = package('a' * 40, inputs, output)
    return output / 'app.tar.gz', metadata['sha256'], metadata['release']


@dataclass
class Scenario:
    fail_backup: bool = False
    fail_start: int = 0
    migrate_on_start: bool = False
    fail_stop: int = 0
    fail_history_after_start: bool = False
    running: bool = True
    changed: bool = False
    stops: int = 0
    starts: int = 0
    events: list[str] = field(default_factory=list)


def fake_host(scenario: Scenario) -> type[Host]:
    class Fake(Host):
        def preflight(self) -> dict[str, Any]:
            scenario.events.append('preflight')
            return dict(NODE)

        def stop(self) -> None:
            scenario.events.append('stop')
            scenario.stops += 1
            if scenario.fail_stop:
                scenario.fail_stop -= 1
                raise RuntimeError('fixture stop failure')
            scenario.running = False

        def start(self) -> None:
            scenario.events.append('start')
            scenario.starts += 1
            scenario.running = True
            if scenario.migrate_on_start:
                scenario.changed = True
            if scenario.fail_start:
                scenario.fail_start -= 1
                raise RuntimeError('fixture startup failure')

        def backup(self) -> str:
            scenario.events.append('backup')
            if scenario.fail_backup:
                raise RuntimeError('fixture backup failure')
            path = self.directory / 'databases.sql.gz'
            path.write_bytes(b'fixture backup')
            return digest(path)

        def history(self) -> dict[str, str]:
            scenario.events.append('history')
            if scenario.fail_history_after_start and scenario.starts:
                raise RuntimeError('fixture history unavailable')
            return {name: ('b' if scenario.changed else 'a') * 64 for name in DATABASES}

        def unchanged_node(self, before: dict[str, Any]) -> None:
            if before != NODE:
                raise ValueError('fixture node changed')

    return Fake
