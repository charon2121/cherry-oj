#!/usr/bin/env python3
"""在真实服务与节点上回归同题多语言、标定切换和历史输入冻结。"""
from __future__ import annotations

from collections.abc import Callable
import re
import secrets
import time
from typing import Any
import uuid

from business_api import API, fixture_zip


CPP_PROGRAM = '#include <cstdio>\nint main(){long long a,b;scanf("%lld%lld",&a,&b);printf("%lld\\n",a+b);}\n'
C_PROGRAM = '#include <stdio.h>\nint main(void){long long a,b;scanf("%lld%lld",&a,&b);printf("%lld\\n",a+b);}\n'
CPP_SOLUTION = 'class Solution { public: long long add(long long a,long long b){return a+b;} };\n'
C_SOLUTION = 'long long add(long long a,long long b){return a+b;}\n'
CPP_DRIVER = '#include <cstdio>\n#include "solution.cpp"\nint main(){long long a,b;scanf("%lld%lld",&a,&b);printf("%lld\\n",Solution().add(a,b));}\n'
C_DRIVER = '#include <stdio.h>\n#include "solution.c"\nint main(void){long long a,b;scanf("%lld%lld",&a,&b);printf("%lld\\n",add(a,b));}\n'


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def languages(api: API, base: str, settings: list[dict[str, Any]]) -> None:
    current = api.request('GET', base)
    api.request('PUT', base + '/languages', dict(rowVersion=current['rowVersion'], allowedLanguages=settings))


def limits(api: API, base: str) -> dict[str, dict[str, Any]]:
    return {row['languageId']: row for row in api.request('GET', base + '/limits')['languages']}


def calibrate(api: API, base: str, language: str, source: str, kind: str, cpu_ns: int, memory_bytes: int) -> str:
    result = api.request('POST', base + '/calibration', dict(languageId=language, kind=kind, cpuNs=cpu_ns,
                         memoryBytes=memory_bytes, clockNs=None, referenceSource=source))
    require(result['status'] == 'VALID' and result['kind'] == kind, '真实标定未成功')
    return result['id']


def submit(api: API, problem_id: str, language: str, source: str, verdict: str = 'AC') -> dict[str, Any]:
    result = api.request('POST', '/api/submissions', dict(problemId=problem_id, languageId=language, source=source),
                         expected=201, headers={'X-Expected-User-Id': api.user_id, 'Idempotency-Key': str(uuid.uuid4())})
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        result = api.request('GET', '/api/submissions/' + result['id'])
        if result['status'] == 'DONE':
            require(result['verdict'] == verdict and result['languageId'] == language, '正式提交结论或语言不符')
            return result
        time.sleep(.2)
    raise TimeoutError('多语言正式提交未在期限内完成')


def trial(api: API, problem_id: str, language: str, source: str, cpu_ns: int, memory_bytes: int) -> None:
    result = api.request('POST', '/api/custom-runs', dict(problemId=problem_id, languageId=language,
                         source=source, inputText='17 -9\n'), headers={'X-Expected-User-Id': api.user_id})
    require(result['status'] == 'COMPLETED' and result['stdout']['text'].strip() == '8', '自测未执行所选语言的程序')
    require(result['effectiveLimits']['cpuNs'] == cpu_ns
            and result['effectiveLimits']['memoryBytes'] == memory_bytes, '自测改变了语言的有效限制')


def prepare(api: API, identity: str, mode: str) -> tuple[str, str, list[dict[str, Any]], dict[str, str]]:
    slug = 'regression-' + identity + '-' + mode.lower()
    problem = api.request('POST', '/api/admin/problems', dict(slug=slug, title='多语言回归 ' + mode,
                          difficulty='EASY', codeMode=mode, languageId='cpp'), expected=201)
    base = '/api/admin/problems/' + problem['id']
    api.request('PATCH', base, dict(slug=slug, title=problem['title'], statementMarkdown='计算两个整数的和。',
                inputDescriptionMarkdown='两个整数。', outputDescriptionMarkdown='它们的和。', constraintsMarkdown=None,
                hintMarkdown=None, difficulty='EASY', tags=[], starterCode='', rowVersion=problem['rowVersion'],
                samples=[dict(ordinal=1, input='1 2', output='3', explanationMarkdown=None)]))
    boundary = 'regression-' + secrets.token_hex(16)
    upload = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="cases.zip"\r\n'
              'Content-Type: application/zip\r\n\r\n').encode() + fixture_zip() + f'\r\n--{boundary}--\r\n'.encode()
    api.request('PUT', base + '/test-data', upload, content_type='multipart/form-data; boundary=' + boundary)
    settings = [dict(id=lang, starterCode='', judgeTemplate=driver if mode == 'CORE' else None)
                for lang, driver in (('cpp', CPP_DRIVER), ('c', C_DRIVER))]
    languages(api, base, settings)
    sources = dict(cpp=CPP_SOLUTION, c=C_SOLUTION) if mode == 'CORE' else dict(cpp=CPP_PROGRAM, c=C_PROGRAM)
    return problem['id'], slug, settings, sources


def run(api: API, identity: str, frozen: Callable[[str], dict[str, Any]]) -> dict[str, Any]:
    """只创建本次标识的题目；调用者负责提供自己环境内的只读快照查询。"""
    if not re.fullmatch(r'[a-z0-9-]{1,48}', identity) or not api.user_id:
        raise ValueError('回归需要本次运行标识与已登录账号')
    result = {}
    for mode in ('ACM', 'CORE'):
        problem_id, slug, settings, sources = prepare(api, identity, mode)
        base = '/api/admin/problems/' + problem_id
        # CORE 的 C 驱动暂缺时，C++ 单独就绪仍可公开。
        if mode == 'CORE':
            languages(api, base, [settings[0], dict(settings[1], judgeTemplate=None)])
        cpp_base = calibrate(api, base, 'cpp', sources['cpp'], 'BASE', 1_000_000_000, 256 << 20)
        check = api.request('GET', base + '/publish-check')
        require(check['ready'] is True, '至少一门语言就绪仍无法公开')
        current = api.request('GET', base)
        api.request('POST', base + '/publish', dict(rowVersion=current['rowVersion']))
        if mode == 'CORE':
            ready = api.request('GET', '/api/problems/' + slug + '/languages')['items']
            require([row['id'] for row in ready] == ['cpp'], '驱动缺失的语言被作为就绪语言')
            languages(api, base, settings)
        ready = api.request('GET', '/api/problems/' + slug + '/languages')['items']
        require([row['id'] for row in ready] == ['cpp', 'c'], 'C/C++ 未同时就绪')
        require(limits(api, base)['c']['limitSource'] == 'DERIVED', 'C 未从 C++ 基础标定推导')
        for language in ('cpp', 'c'):
            trial(api, problem_id, language, sources[language], 1_000_000_000, 256 << 20)
            submission = submit(api, problem_id, language, sources[language])
            require(submission['testcaseCount'] == 6, '正式提交未运行全部测试点')
        derived = frozen(submission['id'])
        require(derived['limitSource'] == 'DERIVED' and derived['languageCalibrationId'] == cpp_base,
                'C 的冻结推导依据不符')
        c_override = calibrate(api, base, 'c', sources['c'], 'OVERRIDE', 2_000_000_000, 128 << 20)
        current = limits(api, base)['c']
        require(current['limitSource'] == 'OVERRIDE' and current['calibrationId'] == c_override,
                '覆盖标定未替换推导限制')
        trial(api, problem_id, 'c', sources['c'], 2_000_000_000, 128 << 20)
        overridden = submit(api, problem_id, 'c', sources['c'])
        require(frozen(overridden['id'])['limitSource'] == 'OVERRIDE', '覆盖限制没有冻结')
        c_base = calibrate(api, base, 'c', sources['c'], 'BASE', 1_000_000_000, 128 << 20)
        current = limits(api, base)
        require(current['c']['limitSource'] == 'BASE' and current['cpp']['limitSource'] == 'DERIVED',
                '切换基础语言后没有重新推导')
        # 关闭基础语言入口仍保留其事实，其他语言与历史不受影响；恢复入口后可继续使用。
        languages(api, base, [settings[0]])
        current = limits(api, base)['cpp']
        require(current['limitSource'] == 'DERIVED' and current['calibrationId'] == c_base,
                '关闭基础语言入口删除了标定依据')
        ready = api.request('GET', '/api/problems/' + slug + '/languages')['items']
        require([row['id'] for row in ready] == ['cpp'], '关闭的语言仍出现在就绪列表')
        submit(api, problem_id, 'cpp', sources['cpp'])
        require(frozen(submission['id']) == derived, '标定或语言修改改变了历史 JudgeInput')
        require(api.request('GET', '/api/submissions/' + submission['id'])['verdict'] == 'AC', '关闭语言破坏历史读取')
        languages(api, base, settings)
        require(limits(api, base)['c']['calibrationId'] == c_base, '恢复入口后丢失基础标定')
        if mode == 'CORE':
            error = submit(api, problem_id, 'c', sources['c'].replace('a+b', 'missingCName'), 'CE')
            require('solution.c:1:' in error['message'], 'C CORE 编译错误没有映射到编辑器行号')
        # 成功后取消公开，永久测试环境不积累公开回归题；失败现场保留给调用者诊断。
        current = api.request('GET', base)
        api.request('POST', base + '/unpublish', dict(rowVersion=current['rowVersion']))
        result[mode] = dict(problemId=problem_id, derivedSubmissionId=submission['id'],
                            overrideSubmissionId=overridden['id'], baseCalibrationId=c_base,
                            frozenSha256=derived['sha256'])
    return result
