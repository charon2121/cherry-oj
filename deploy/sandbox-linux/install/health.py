#!/usr/bin/env python3
"""Bounded readiness check; never logs credentials or execution output."""
import http.client
import json
import sys
import time


def ready(mode='judge'):
    """judge 只在执行层启动冒烟与原生部署自检（要求 linux 隔离）都通过后才监听，
    所以能答 /version 就说明它已在隔离执行器上就绪。"""
    if mode != 'judge':
        raise ValueError('unknown health target')
    connection = http.client.HTTPConnection('127.0.0.1', 15051, timeout=1)
    try:
        connection.request('GET', '/version')
        response = connection.getresponse()
        body = response.read(16385)
        if response.status != 200 or len(body) > 16384:
            return False
        return json.loads(body).get('name') == 'cherry-oj-judge'
    except (OSError, ValueError, http.client.HTTPException):
        return False
    finally:
        connection.close()


def main():
    mode = sys.argv[1]
    if mode != 'judge':
        raise ValueError('unknown health target')
    # judge 启动要先逐文件核对 rootfs、再用一次真实执行冒烟，C++ 工具链 rootfs 需要几秒到十几秒。
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        if ready(mode):
            return
        time.sleep(.1)
    raise RuntimeError(mode + ' readiness timeout')


if __name__ == '__main__':
    main()
