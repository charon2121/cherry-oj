#!/usr/bin/env python3
"""通过既有 systemd 与 MySQL 客户端管理应用，不接触凭据正文。"""

from __future__ import annotations

import gzip
import hashlib
import ipaddress
import json
import os
import re
import selectors
import signal
import subprocess
import time
import urllib.request
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from artifacts import DATABASES, SERVICES, digest, relative_path


STOP_ORDER = ('gateway', 'submission', 'judging', 'problem', 'user')
START_ORDER = ('user', 'problem', 'submission', 'judging', 'gateway')


@dataclass(frozen=True)
class Mysql:
    container: str
    defaultsFile: str
    databasePrefix: str

    def __post_init__(self) -> None:
        if any(not isinstance(value, str) for value in (self.container, self.defaultsFile, self.databasePrefix)):
            raise ValueError('MySQL 客户端参数必须是字符串')
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,127}', self.container):
            raise ValueError('MySQL 容器名无效')
        if not self.defaultsFile.startswith('/'):
            raise ValueError('MySQL 客户端配置必须是绝对路径')
        relative_path(self.defaultsFile[1:])
        if not re.fullmatch(r'[a-z][a-z0-9_]{0,47}', self.databasePrefix):
            raise ValueError('数据库前缀无效')

    def client(self, binary: str = 'mysql') -> list[str]:
        return ['docker', 'exec', self.container, binary, '--defaults-extra-file=' + self.defaultsFile]


class Host:
    def __init__(self, mysql: Mysql, ports: dict[str, int], directory: Path) -> None:
        self.mysql = mysql
        self.ports = ports
        self.directory = directory
        self.sequence = max((int(path.name.split('-')[0]) for path in directory.glob('*-command.log')
                             if re.fullmatch(r'[0-9]+-command\.log', path.name)), default=0)

    def command(self, argv: list[str], timeout: int = 45) -> str:
        self.sequence += 1
        log = self.directory / f'{self.sequence:03d}-command.log'
        with log.open('xb') as output:
            log.chmod(0o600)
            process = subprocess.Popen(argv, stdout=output, stderr=output, start_new_session=True)
            try:
                if process.wait(timeout=timeout):
                    raise RuntimeError('命令失败，详见 ' + str(log))
            finally:
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
        if log.stat().st_size > 1 << 20:
            raise ValueError('命令响应超限，详见 ' + str(log))
        return log.read_text().strip()

    def unit(self, name: str) -> dict[str, str]:
        text = self.command(['systemctl', 'show', name, '-p', 'LoadState', '-p', 'ActiveState', '-p', 'MainPID'])
        result = dict(line.split('=', 1) for line in text.splitlines())
        if result.get('LoadState') != 'loaded':
            raise ValueError('系统服务未安装: ' + name)
        return result

    def node(self) -> dict[str, str | int]:
        state = self.unit('cherry-sandbox-judge.service')
        release = Path('/opt/cherry-oj/current').resolve(strict=True)
        if (release.parent != Path('/opt/cherry-oj/releases') or state.get('ActiveState') != 'active'
                or not state.get('MainPID', '').isdigit() or int(state['MainPID']) <= 0):
            raise ValueError('原生判题节点未在正常运行')
        return dict(release=release.name, mainPid=int(state['MainPID']), activeState=state['ActiveState'])

    def unchanged_node(self, before: dict[str, Any]) -> None:
        if self.node() != before:
            raise ValueError('原生判题节点在应用操作期间发生变化')

    def sql(self, query: str) -> str:
        return self.command(self.mysql.client() + ['-N', '-B', '-r', '-e', query], timeout=20)

    def history(self) -> dict[str, str]:
        result = {}
        for service in DATABASES:
            query = ("SET TRANSACTION READ ONLY; START TRANSACTION; SELECT JSON_OBJECT("
                     "'installed_rank',installed_rank,'version',version,'description',description,"
                     "'type',type,'script',script,'checksum',checksum,'success',success) FROM "
                     + self.mysql.databasePrefix + service + '.flyway_schema_history ORDER BY installed_rank; ROLLBACK;')
            rows = [json.loads(line) for line in self.sql(query).splitlines() if line]
            if not rows or any(type(row.get('success')) is not int or row['success'] != 1 for row in rows):
                raise ValueError('数据库迁移历史缺失或包含失败记录: ' + service)
            result[service] = hashlib.sha256(json.dumps(rows, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
        return result

    def app_units(self) -> None:
        for service in SERVICES:
            text = self.command(['systemctl', 'show', 'cherry-app-' + service + '.service',
                                 '-p', 'User', '-p', 'Group', '-p', 'WorkingDirectory', '-p', 'ExecStart'])
            properties = dict(line.split('=', 1) for line in text.splitlines())
            expected_jar = '/opt/cherry-app/current/jars/' + service + '-service.jar'
            if (properties.get('User') != 'cherry-app-' + service
                    or properties.get('Group') != 'cherry-app-' + service
                    or properties.get('WorkingDirectory') != '/var/lib/cherry-app/' + service
                    or not re.search(r'(?:^|\s)-jar\s+' + re.escape(expected_jar) + r'(?:\s|;|$)',
                                     properties.get('ExecStart', ''))):
                raise ValueError('系统服务未使用已约定的账号、数据目录或 current 入口: ' + service)

    def listeners(self) -> None:
        expected = set(self.ports.values()) | {18080}
        found = set()
        for line in self.command(['ss', '-H', '-ltn'], timeout=15).splitlines():
            parts = line.split()
            if len(parts) < 5:
                raise ValueError('无法解析本机 TCP 监听状态')
            address, _, raw_port = parts[3].rpartition(':')
            if not raw_port.isdigit() or int(raw_port) not in expected:
                continue
            ip = ipaddress.ip_address(address.strip('[]'))
            mapped = ip.ipv4_mapped if isinstance(ip, ipaddress.IPv6Address) else None
            if not ip.is_loopback and not (mapped is not None and mapped.is_loopback):
                raise ValueError('应用端口未限制为本机回环: ' + raw_port)
            found.add(int(raw_port))
        if found != expected:
            raise ValueError('应用服务或 Web 缺少约定的本机监听端口')

    def preflight(self) -> dict[str, str | int]:
        self.app_units()
        self.command(['nginx', '-t'])
        # 同时限制容器内的备份进程；仅超时杀死 docker 客户端不能保证其子命令退出。
        self.command(['docker', 'exec', self.mysql.container, 'timeout', '--version'], timeout=15)
        if self.sql('SELECT 1;') != '1':
            raise ValueError('MySQL 客户端检查失败')
        self.history()
        for service in SERVICES:
            self.health(service)
        self.listeners()
        return self.node()

    def stop(self) -> None:
        for service in STOP_ORDER:
            unit = 'cherry-app-' + service + '.service'
            self.command(['systemctl', 'stop', unit])
            state = self.unit(unit)
            if state.get('ActiveState') not in ('inactive', 'failed') or state.get('MainPID') != '0':
                raise RuntimeError('应用服务没有停止: ' + service)

    def get(self, url: str) -> bytes:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(url, timeout=3) as response:
            value = response.read((64 << 10) + 1)
            if response.status != 200 or len(value) > 64 << 10:
                raise ValueError('本机健康检查响应失败或超限')
            return value

    def health(self, service: str) -> None:
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            state = self.unit('cherry-app-' + service + '.service')
            if state.get('ActiveState') in ('failed', 'inactive'):
                raise RuntimeError('应用服务已经退出: ' + service)
            try:
                value = json.loads(self.get(f'http://127.0.0.1:{self.ports[service]}/actuator/health'))
                if value.get('status') == 'UP':
                    return
            except (OSError, ValueError):
                pass
            time.sleep(.5)
        raise TimeoutError('应用服务健康检查超时: ' + service)

    def start(self) -> None:
        self.app_units()
        for service in START_ORDER:
            self.command(['systemctl', 'start', 'cherry-app-' + service + '.service'])
            self.health(service)
            print(json.dumps(dict(service=service, status='UP')), flush=True)
        self.get('http://127.0.0.1:18080/')
        self.listeners()

    def backup(self) -> str:
        partial = self.directory / 'databases.sql.gz.partial'
        errors = self.directory / 'mysqldump.log'
        argv = ['docker', 'exec', self.mysql.container, 'timeout', '-k', '5s', '180s', 'mysqldump',
                '--defaults-extra-file=' + self.mysql.defaultsFile, '--single-transaction', '--no-tablespaces',
                '--databases', *(self.mysql.databasePrefix + service for service in DATABASES)]
        deadline = time.monotonic() + 180
        total = 0
        with errors.open('xb') as error:
            errors.chmod(0o600)
            process = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=error, start_new_session=True)
            try:
                with selectors.DefaultSelector() as events, gzip.open(partial, 'xb') as output:
                    partial.chmod(0o600)
                    events.register(process.stdout, selectors.EVENT_READ)
                    while True:
                        remaining = deadline - time.monotonic()
                        if remaining <= 0:
                            raise TimeoutError('数据库备份超过180秒')
                        if not events.select(timeout=min(remaining, 1)):
                            continue
                        chunk = os.read(process.stdout.fileno(), 65536)
                        if not chunk:
                            break
                        total += len(chunk)
                        output.write(chunk)
                if process.wait(timeout=max(.01, deadline - time.monotonic())):
                    raise RuntimeError('数据库备份失败，详见 ' + str(errors))
                if not total:
                    raise ValueError('数据库备份未产生内容')
            finally:
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                process.stdout.close()
        target = self.directory / 'databases.sql.gz'
        partial.rename(target)
        with target.open('rb') as incoming:
            os.fsync(incoming.fileno())
        return digest(target)

    def settings(self) -> dict[str, str]:
        return asdict(self.mysql)
