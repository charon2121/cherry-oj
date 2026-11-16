#!/usr/bin/env python3
"""通过正式判题入口验证 Java 编译产物、JIT、诊断与资源隔离。"""
from __future__ import annotations

import json
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


def program(body: str) -> str:
    return 'public class Main { public static void main(String[] args) throws Exception { ' + body + ' } }\n'


@dataclass(frozen=True)
class Case:
    name: str
    source: str
    verdict: str
    output: str | None = None
    solution: str | None = None
    stderr: str | None = ''
    message: str | None = None
    cpu_ns: int = 2_000_000_000
    memory_bytes: int = 256 << 20


CASES = (
    Case('standard-library', program('var p=new java.util.PriorityQueue<Integer>();p.add(3);p.add(1);'
         'if(p.remove()!=1||!new java.math.BigInteger("123").pow(2).toString().equals("15129"))throw new AssertionError();'
         'System.out.println("标准库正常");'), 'RAN', '标准库正常\n'),
    Case('jit', 'public class Main { static long hot(int n){long x=0;for(int i=0;i<n;i++)x=(x+i)%1000000007;return x;}'
         'public static void main(String[] args){System.out.println(hot(1000000));'
         'var compiler=java.lang.management.ManagementFactory.getCompilationMXBean();'
         'if(compiler==null||compiler.getTotalCompilationTime()<=0)throw new AssertionError("JIT inactive");}}',
         'RAN', '999496507\n'),
    Case('core-classes', program('System.out.println(new Solution().message());'), 'RAN', '中文结果\n',
         'import java.util.function.Supplier;\npublic class Solution {\n'
         'static class Inner { String text="中文结果"; }\n'
         'public String message(){return new Supplier<String>() {public String get(){return new Inner().text;}}.get();}\n}\n'),
    Case('utf8-stderr', program('System.err.println("中文错误");'), 'RAN', '', stderr='中文错误\n'),
    Case('syntax', 'public class Main { broken syntax }', 'CE', message='Main.java:'),
    Case('core-compile-error', program('new Solution().run();'), 'CE', solution=
         'import java.io.*;\nimport java.util.*;\nimport java.math.*;\n'
         'class Solution { public void run(){missingJavaName();} }\n', message='Solution.java:4:'),
    Case('core-runtime-error', program('new Solution().run();'), 'RE', solution=
         'import java.io.*;\nimport java.util.*;\nimport java.math.*;\nclass Solution {\n'
         'public void run(){throw new IllegalStateException("fixture");}\n}\n', stderr=None),
    Case('tle', program('long x=0;while(true){if(++x==0)System.out.println(x);}'), 'TLE', cpu_ns=50_000_000),
    Case('jvm-memory-error', program('System.out.println(new byte[Integer.MAX_VALUE].length);'), 'RE', stderr=None),
    Case('mle', program('var x=new java.util.ArrayList<byte[]>();while(true)x.add(new byte[4<<20]);'),
         'MLE', memory_bytes=128 << 20, stderr=None),
    Case('ole', program('while(true)System.out.print("x".repeat(65536));'), 'OLE'),
    # 输出标记证明已经进入网络操作；后续未允许的调用仍被终止，不能接受普通类加载异常。
    Case('network', program('System.out.println("network-ready");new java.net.Socket("127.0.0.1",80);'),
         'RE', 'network-ready\n'),
)


def validate(case: Case, result: dict[str, Any]) -> None:
    if result.get('verdict') != case.verdict:
        raise ValueError('Java 判定不符: ' + case.name + ': ' + str(result.get('verdict')))
    points = result.get('testcaseResults', [])
    if case.verdict == 'CE':
        if points != [] or (case.message is not None and case.message not in result.get('message', '')):
            raise ValueError('Java 编译诊断不符: ' + case.name)
        return
    if (not isinstance(points, list) or len(points) != 1 or points[0].get('idx') != 1
            or points[0].get('verdict') != case.verdict):
        raise ValueError('Java 测试点判定不符: ' + case.name)
    point = points[0]
    if case.output is not None and point.get('output', {}).get('excerpt') != case.output:
        raise ValueError('Java 输出不符: ' + case.name)
    stderr = point.get('stderr', {})
    if case.stderr is not None and (stderr.get('excerpt') != case.stderr
                                   or stderr.get('bytes') != len(case.stderr.encode())):
        raise ValueError('Java 运行产生警告或诊断不符: ' + case.name)
    if case.name == 'core-runtime-error' and 'Solution.java:5' not in stderr.get('excerpt', ''):
        raise ValueError('judge 改写或丢失了原始 Java 用户文件行号')
    if case.name == 'jvm-memory-error' and 'OutOfMemoryError' not in stderr.get('excerpt', ''):
        raise ValueError('JVM 内存异常用例没有产生原生诊断')


def run(endpoint: str, cases: tuple[Case, ...] = CASES) -> list[dict[str, Any]]:
    parsed = urllib.parse.urlsplit(endpoint)
    if (parsed.scheme != 'http' or parsed.hostname != '127.0.0.1' or parsed.username is not None
            or parsed.password is not None or parsed.path not in ('', '/') or parsed.query or parsed.fragment):
        raise ValueError('Java 回归只连接本机测试节点')
    results = []
    for case in cases:
        body = dict(submissionId='java-runtime-' + case.name, problemId='java-runtime', languageId='java',
                    mode='trial', source=case.source, testcases=[dict(input='1 2\n')],
                    limits=dict(cpuNs=case.cpu_ns, memoryBytes=case.memory_bytes, clockNs=5_000_000_000))
        if case.solution is not None:
            body['solutionSource'] = case.solution
        request = urllib.request.Request(endpoint.rstrip('/') + '/judge', json.dumps(body).encode(),
                                         {'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=40) as response:
            raw = response.read((1 << 20) + 1)
            if response.status != 200 or len(raw) > 1 << 20:
                raise ValueError('Java 回归响应超限或失败: ' + case.name)
        result = json.loads(raw)
        validate(case, result)
        results.append(dict(name=case.name, result=result))
    return results
