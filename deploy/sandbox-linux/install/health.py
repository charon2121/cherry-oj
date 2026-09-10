#!/usr/bin/env python3
"""Bounded readiness check; never logs credentials or execution output."""
import http.client
import json
from pathlib import Path
import sys
import time


def unix_listener_present(table, path):
    for row in table.splitlines()[1:]:
        fields = row.split(maxsplit=7)
        if len(fields) == 8 and fields[7] == path:
            try:
                if int(fields[3], 16) & 0x10000 and fields[4:6] == ['0001', '01']:
                    return True
            except ValueError:
                continue
    return False


def ready(mode):
    if mode == 'helper':
        # A crash leaves an inode before the replacement helper finishes its probes.
        # Check the kernel listener table without consuming a protocol slot.
        path = '/run/cherry-sandbox-helper/helper.sock'
        return (Path(path).is_socket() and
                unix_listener_present(Path('/proc/net/unix').read_text(), path))
    port = {'sandbox': 15050, 'judge': 15051}[mode]
    connection = http.client.HTTPConnection('127.0.0.1', port, timeout=1)
    try:
        connection.request('GET', '/version')
        response = connection.getresponse()
        body = response.read(16385)
        if response.status != 200 or len(body) > 16384:
            return False
        result = json.loads(body)
        expected = 'cherry-oj-judge' if mode == 'judge' else 'cherry-oj-sandbox'
        return result.get('name') == expected and (mode == 'judge' or result.get('isolation') == 'linux')
    except (OSError, ValueError, http.client.HTTPException):
        return False
    finally:
        connection.close()


def main():
    mode = sys.argv[1]
    if mode not in ('helper', 'sandbox', 'judge'):
        raise ValueError('unknown health target')
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        if ready(mode):
            return
        time.sleep(.1)
    raise RuntimeError(mode + ' readiness timeout')


if __name__ == '__main__':
    main()
