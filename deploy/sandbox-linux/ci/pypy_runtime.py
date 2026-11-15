#!/usr/bin/env python3
"""通过正式判题入口验证 PyPy 运行时、JIT 与资源隔离。"""
from __future__ import annotations

from dataclasses import dataclass
import json
from typing import Any
import urllib.parse
import urllib.request


@dataclass(frozen=True)
class Case:
    name: str
    source: str
    verdict: str
    output: str | None = None
    solution: str | None = None
    cpu_ns: int = 2_000_000_000
    memory_bytes: int = 256 << 20


CASES = (
    Case('imports', 'import sys,ssl,sqlite3,bz2,lzma,zlib,ctypes,decimal,statistics,readline\nprint("imports-ok")\n',
         'RAN', 'imports-ok\n'),
    Case('jit', 'import pypyjit\nseen=[]\npypyjit.set_compile_hook(lambda info:seen.append(info.type))\n'
         'x=0\nfor i in range(1000000): x=(x+i)%1000000007\nassert seen, "JIT did not compile"\nprint("jit-ok")\n',
         'RAN', 'jit-ok\n'),
    Case('core', 'from solution import Solution\na,b=map(int,input().split())\nprint(Solution().add(a,b))\n',
         'RAN', '3\n', 'import sys\nclass Solution:\n    def add(self,a,b):\n        return a+b\n'),
    Case('syntax', 'def broken(:\n', 'RE'),
    Case('core-error', 'from solution import Solution\n', 'RE', solution='import sys\ndef broken(:\n'),
    Case('tle', 'while True: pass\n', 'TLE', cpu_ns=50_000_000),
    Case('mle', 'x=[]\nwhile True: x.append(bytearray(4<<20))\n', 'MLE', memory_bytes=128 << 20),
    Case('ole', 'while True: print("x"*65536)\n', 'OLE'),
    # 当前过滤器终止调用 socket 的进程；若仅因模块缺失或导入失败退出，会留下 traceback，不能算隔离通过。
    Case('network', 'import socket\nsocket.socket()\n', 'RE'),
)


def run(endpoint: str, cases: tuple[Case, ...] = CASES) -> list[dict[str, Any]]:
    parsed = urllib.parse.urlsplit(endpoint)
    if (parsed.scheme != 'http' or parsed.hostname != '127.0.0.1' or parsed.username is not None
            or parsed.password is not None or parsed.path not in ('', '/') or parsed.query or parsed.fragment):
        raise ValueError('运行时回归只连接本机测试节点')
    results = []
    for case in cases:
        body = dict(submissionId='pypy-runtime-' + case.name, problemId='pypy-runtime', languageId='pypy',
                    mode='trial', source=case.source, testcases=[dict(input='1 2\n')],
                    limits=dict(cpuNs=case.cpu_ns, memoryBytes=case.memory_bytes, clockNs=5_000_000_000))
        if case.solution is not None:
            body['solutionSource'] = case.solution
        request = urllib.request.Request(endpoint.rstrip('/') + '/judge', json.dumps(body).encode(),
                                         {'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=15) as response:
            raw = response.read((1 << 20) + 1)
            if response.status != 200 or len(raw) > 1 << 20:
                raise ValueError('运行时回归响应超限或失败: ' + case.name)
        result = json.loads(raw)
        points = result.get('testcaseResults')
        if (result.get('verdict') != case.verdict or not isinstance(points, list) or len(points) != 1
                or points[0].get('idx') != 1 or points[0].get('verdict') != case.verdict):
            raise ValueError('运行时回归判定不符: ' + case.name)
        point = points[0]
        if case.output is not None and point.get('output', {}).get('excerpt') != case.output:
            raise ValueError('运行时回归输出不符: ' + case.name)
        stderr = point.get('stderr', {})
        if case.name in ('imports', 'jit', 'core', 'network') and stderr.get('bytes') != 0:
            raise ValueError('正常运行产生了警告，或网络用例因导入错误退出: ' + case.name)
        if case.name in ('syntax', 'core-error') and 'SyntaxError' not in stderr.get('excerpt', ''):
            raise ValueError('语法错误没有保留原生诊断: ' + case.name)
        if case.name == 'core-error' and 'solution.py", line 2' not in stderr.get('excerpt', ''):
            raise ValueError('judge 改写了应由 Java 处理的原始用户文件行号')
        results.append(dict(name=case.name, result=result))
    return results
