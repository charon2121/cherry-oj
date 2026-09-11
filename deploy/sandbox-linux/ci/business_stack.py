"""Start the real services on a disposable VM, with bounded units and fresh dependencies."""
import json
import os
from pathlib import Path
import pwd
import socket
import time
import urllib.error
import urllib.request

from business_config import IMAGES, PORTS, SERVICES, create
from business_resources import Dependencies
from command import run
from owned import BASE, PREFIX
from report import ROOT

PRIVATE = BASE / 'business'


def wait_health(port, seconds=90):
    deadline = time.monotonic() + seconds
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    while time.monotonic() < deadline:
        try:
            with opener.open(f'http://127.0.0.1:{port}/actuator/health/readiness', timeout=2) as response:
                if json.loads(response.read(65536))['status'] == 'UP':
                    return
        except (OSError, ValueError, KeyError):
            pass
        time.sleep(.25)
    raise TimeoutError('service readiness deadline: ' + str(port))


class Stack:
    def __init__(self, owned, output, java):
        self.owned, self.output, self.java = owned, Path(output), str(Path(java).resolve(strict=True))
        self.runner = pwd.getpwuid(int(os.environ['SUDO_UID']))
        if self.runner.pw_uid == 0 or self.runner.pw_name != 'runner':
            raise RuntimeError('expected non-root GitHub runner identity')
        available = next(int(line.split()[1]) for line in Path('/proc/meminfo').read_text().splitlines()
                         if line.startswith('MemAvailable:'))
        if available < 10 * 1024 * 1024:
            raise RuntimeError('business VM requires 10 GiB available memory')
        for port in (*PORTS.values(), 4173, 13306, 16379, 19092, 19093):
            with socket.socket() as sock:
                sock.bind(('127.0.0.1', port))
        self.credentials = create(PRIVATE)
        self.dependencies = Dependencies(PRIVATE, output)
        run(['bash', ROOT / 'scripts/identity-keys', 'init', PRIVATE / 'keys'], self.output / 'keys.log', 20)
        for path in [PRIVATE, *PRIVATE.rglob('*')]:
            os.chown(path, self.runner.pw_uid, self.runner.pw_gid)
        # Mounted individual files must be readable by the containers' own non-root users.
        # The host parent remains mode 0700; neither file is part of public artifacts.
        for name in ('init.sql', 'redis.conf'):
            (PRIVATE / name).chmod(0o444)

    def unit(self, name, argv, *, wait=False, seconds=1500, memory=768, environment=None, cwd=None):
        unit = PREFIX + 'business-' + name + '-' + self.owned.identity
        self.owned.register(unit)
        properties = [f'User={self.runner.pw_uid}', f'Group={self.runner.pw_gid}',
                      f'MemoryMax={memory}M', 'MemorySwapMax=0', 'CPUQuota=100%', 'TasksMax=128',
                      f'RuntimeMaxSec={seconds}', 'KillMode=control-group', 'TimeoutStopSec=5s',
                      'LimitFSIZE=8M', 'UMask=0077', 'StandardInput=null',
                      'StandardOutput=append:' + str(PRIVATE / (name + '.log')), 'StandardError=inherit',
                      'WorkingDirectory=' + str(cwd or PRIVATE)]
        cmd = ['systemd-run', '--collect', '--unit=' + unit]
        if wait:
            cmd += ['--wait']
        for prop in properties:
            cmd += ['-p', prop]
        for key, value in (environment or {}).items():
            cmd += ['--setenv=' + key + '=' + value]
        run([*cmd, *argv], self.output / (name + '-start.log'), seconds + 10 if wait else 10)

    def start_dependencies(self):
        db = self.dependencies
        db.start('mysql', IMAGES['mysql'], ['-p', '127.0.0.1:13306:3306',
                 '--env-file', str(PRIVATE / 'mysql.env'),
                 '-v', str(PRIVATE / 'init.sql') + ':/docker-entrypoint-initdb.d/00-init.sql:ro',
                 '-v', str(PRIVATE / 'mysql.cnf') + ':/run/cherry-client.cnf:ro', IMAGES['mysql']], memory=1024)
        db.start('redis', IMAGES['redis'], ['-p', '127.0.0.1:16379:6379',
                 '-v', str(PRIVATE / 'redis.conf') + ':/run/cherry-redis.conf:ro',
                 IMAGES['redis'], 'redis-server', '/run/cherry-redis.conf'], memory=128)
        kafka = {'KAFKA_NODE_ID': '1', 'KAFKA_PROCESS_ROLES': 'broker,controller',
                 'KAFKA_LISTENERS': 'PLAINTEXT://:19092,CONTROLLER://:19093',
                 'KAFKA_ADVERTISED_LISTENERS': 'PLAINTEXT://127.0.0.1:19092',
                 'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP': 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT',
                 'KAFKA_CONTROLLER_LISTENER_NAMES': 'CONTROLLER',
                 'KAFKA_CONTROLLER_QUORUM_VOTERS': '1@localhost:19093',
                 'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR': '1',
                 'KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR': '1',
                 'KAFKA_TRANSACTION_STATE_LOG_MIN_ISR': '1',
                 'KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS': '0',
                 'KAFKA_LOG_DIRS': '/mnt/shared/config/data'}
        options = ['-p', '127.0.0.1:19092:19092', '--tmpfs', '/etc/kafka/secrets:rw,noexec,nosuid,size=1m']
        for key, value in kafka.items():
            options += ['-e', key + '=' + value]
        db.start('kafka', IMAGES['kafka'], [*options, IMAGES['kafka']], memory=1024)
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                value = db.docker('exec', 'cherry-ci-' + self.owned.identity + '-mysql', 'mysql',
                                  '--defaults-extra-file=/run/cherry-client.cnf', '--protocol=TCP', '--host=127.0.0.1',
                                  '--batch', '--skip-column-names',
                                  '-e', "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name LIKE 'cherry_ci_%'")
                if value == '4':
                    return
            except RuntimeError:
                pass
            time.sleep(.5)
        raise TimeoutError('fresh databases were not initialized')

    def java_services(self):
        script = ROOT / 'deploy/sandbox-linux/ci/business_service.py'
        self.unit('bootstrap', ['python3', '-B', script, PRIVATE, 'user', '--java', self.java, '--bootstrap'],
                  wait=True, seconds=90)
        for service in SERVICES:
            self.unit(service, ['python3', '-B', script, PRIVATE, service, '--java', self.java])
            wait_health(PORTS[service])
        if (PRIVATE / 'forbidden-local-testdata').exists():
            raise RuntimeError('judging unexpectedly used local data mode')

    def browser_server(self, node):
        self.unit('preview', [node, ROOT / 'apps/web/node_modules/vite/bin/vite.js', 'preview',
                             '--host', '127.0.0.1', '--port', '4173', '--strictPort'],
                  memory=256, cwd=ROOT / 'apps/web')
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            try:
                with urllib.request.urlopen('http://127.0.0.1:4173', timeout=2) as response:
                    if response.status == 200 and b'<!doctype html>' in response.read(16384).lower():
                        return
            except OSError:
                pass
            time.sleep(.1)
        raise TimeoutError('real web preview did not become ready')

    def diagnose(self):
        # Only safe process state; no raw Java log, environment or container configuration.
        self.dependencies.diagnose()
        units = [u for u in self.owned.data['units'] if 'business-' in u]
        if units:
            run(['systemctl', 'show', *units, '-p', 'Id', '-p', 'ActiveState', '-p', 'Result',
                 '-p', 'ExecMainStatus', '-p', 'MemoryPeak', '-p', 'NRestarts'],
                self.output / 'business-services.json.log', 15)
