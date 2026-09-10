#!/usr/bin/env python3
"""Bounded loopback protocol receiver for deployment tests, not a Java control plane."""
import argparse
import hmac
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from pathlib import Path
import re
import socket
import time
import uuid

MAX_BODY = 64 << 10
MAX_EVENTS = 128


def validate_registration(value, node):
    fields = {'nodeId', 'environmentFingerprint', 'sessionId', 'endpoint', 'architecture', 'cpuModel',
              'osVersion', 'kernelVersion', 'judgeVersion', 'sandboxVersion', 'configDigest', 'languages'}
    if not isinstance(value, dict) or set(value) != fields:
        raise ValueError('registration fields differ from contract')
    if value['nodeId'] != node or value['endpoint'] != 'http://127.0.0.1:15051' or value['architecture'] != 'amd64':
        raise ValueError('registration does not belong to this native node')
    for key in ('environmentFingerprint', 'configDigest'):
        if not isinstance(value[key], str) or not re.fullmatch('[0-9a-f]{64}', value[key]):
            raise ValueError('invalid identity digest')
    uuid.UUID(value['sessionId'])
    for key in ('cpuModel', 'osVersion', 'kernelVersion', 'judgeVersion', 'sandboxVersion'):
        if not isinstance(value[key], str) or not value[key] or len(value[key]) > 1024:
            raise ValueError('missing runtime identity')
    languages = value['languages']
    if not isinstance(languages, list) or len(languages) != 1:
        raise ValueError('expected one real C++ toolchain')
    lang = languages[0]
    if (set(lang) != {'languageId', 'toolchainVersion', 'languageConfigDigest'} or lang['languageId'] != 'cpp'
            or not isinstance(lang['toolchainVersion'], str) or not lang['toolchainVersion']
            or not re.fullmatch('[0-9a-f]{64}', lang['languageConfigDigest'])):
        raise ValueError('invalid toolchain registration')
    return value


class Receiver(HTTPServer):
    request_queue_size = 4

    def __init__(self, directory, node, token, port):
        self.directory, self.node, self.token = directory, node, token
        self.events, self.registration = [], None
        self.environment = str(uuid.uuid4())
        super().__init__(('127.0.0.1', port), Handler)
        self.timeout = .2

    def save(self):
        temp = self.directory / 'events.pending'
        temp.write_text(json.dumps(self.events) + '\n')
        temp.replace(self.directory / 'events.json')


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(2)

    def log_message(self, *_args):
        pass  # No headers or credentials in journal or artifacts.

    def do_POST(self):
        try:
            if not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + self.server.token):
                self.send_error(403)
                return
            lengths = self.headers.get_all('Content-Length', [])
            if len(lengths) != 1 or not lengths[0].isdigit() or self.headers.get('Transfer-Encoding'):
                raise ValueError('explicit bounded content length required')
            length = int(lengths[0])
            if not 0 < length <= MAX_BODY or len(self.server.events) >= MAX_EVENTS:
                raise ValueError('receiver budget exhausted')
            raw = self.rfile.read(length)
            if len(raw) != length:
                raise ValueError('truncated body')
            value = json.loads(raw)
            route = self.path.removeprefix('/internal/judge-nodes/v1/')
            if self.path != '/internal/judge-nodes/v1/' + route:
                raise ValueError('unknown route')
            if route == 'register':
                validate_registration(value, self.server.node)
                previous = self.server.registration
                if previous and {k: v for k, v in previous.items() if k != 'sessionId'} != {k: v for k, v in value.items() if k != 'sessionId'}:
                    raise ValueError('identity changed during native recovery')
                self.server.registration = value
            elif route == 'heartbeat':
                previous = self.server.registration
                if not previous or value != {k: previous[k] for k in ('nodeId', 'sessionId', 'environmentFingerprint')}:
                    raise ValueError('heartbeat is not from registered session')
            else:
                raise ValueError('unknown route')
            self.server.events.append(dict(route=route, value=value))
            self.server.save()
            body = json.dumps(dict(nodeId=self.server.node, environmentId=self.server.environment,
                                   leaseDurationNs=60_000_000_000)).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (ValueError, KeyError, TypeError):
            # A bad registration is a durable test failure even if a later retry succeeds.
            (self.server.directory / 'protocol-error').touch()
            self.send_error(400)
        except (OSError, socket.timeout):
            (self.server.directory / 'protocol-error').touch()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--node', required=True)
    parser.add_argument('--port', type=int, required=True)
    args = parser.parse_args()
    token = (args.directory / 'token').read_text().strip()
    with Receiver(args.directory, args.node, token, args.port) as server:
        server.save()
        (args.directory / 'ready').write_text(str(server.server_port))
        deadline = time.monotonic() + 900
        while time.monotonic() < deadline:
            server.handle_request()


if __name__ == '__main__':
    main()
