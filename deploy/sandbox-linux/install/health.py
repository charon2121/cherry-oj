#!/usr/bin/env python3
"""Bounded readiness check; never logs credentials or execution output."""
import http.client
import json
import sys
import time


def ready(mode):
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
    if mode not in ('sandbox', 'judge'):
        raise ValueError('unknown health target')
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        if ready(mode):
            return
        time.sleep(.1)
    raise RuntimeError(mode + ' readiness timeout')


if __name__ == '__main__':
    main()
