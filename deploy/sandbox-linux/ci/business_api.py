"""执行正常认证与业务准备；每个写请求只发送一次。"""
from __future__ import annotations

import hashlib
import http.cookiejar
import io
import json
import re
import secrets
import stat
import time
import urllib.error
import urllib.request
import zipfile

from report import ROOT

FIXTURES = ROOT / 'deploy/sandbox-linux/tests/acceptance'
MAX_BODY = 1 << 20


def failure_kind(data: bytes | str) -> str:
    # Classify only exact, fixed public errors; never export an arbitrary response field.
    try:
        value = json.loads(data)
    except (ValueError, UnicodeError):
        return 'UNCLASSIFIED'
    if not isinstance(value, dict):
        return 'UNCLASSIFIED'
    code = value.get('code')
    if not isinstance(code, str):
        return 'UNCLASSIFIED'
    known = {'INTERNAL_ERROR': 'INTERNAL_ERROR', 'BAD_GATEWAY': 'BAD_GATEWAY',
             'GATEWAY_TIMEOUT': 'GATEWAY_TIMEOUT'}
    if code in known:
        return known[code]
    detail = value.get('detail')
    if code != 'SERVICE_UNAVAILABLE' or not isinstance(detail, str):
        return 'UNCLASSIFIED'
    return {'身份服务配置不一致，请联系管理员。': 'IDENTITY_CONFIGURATION_MISMATCH',
            '身份信任状态暂时不一致，请稍后重试。': 'IDENTITY_TRUST_MISMATCH',
            '服务暂时不可用，请稍后重试。': 'UPSTREAM_UNAVAILABLE'}.get(detail, 'UNCLASSIFIED')


def fixture_zip():
    buffer = io.BytesIO()
    pairs = [(1, 2), (0, 0), (-17, 9), (1000000000, 1000000000), (-999, -1), (42, -42)]
    with zipfile.ZipFile(buffer, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for index, (a, b) in enumerate(pairs, 1):
            for suffix, content in (('in', f'{a} {b}\n'), ('out', f'{a + b}\n')):
                entry = zipfile.ZipInfo(f'{index}.{suffix}', date_time=(2026, 1, 1, 0, 0, 0))
                entry.create_system = 3
                entry.external_attr = (stat.S_IFREG | 0o600) << 16
                archive.writestr(entry, content)
    return buffer.getvalue()


class API:
    def __init__(self, output=None):
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}),
                                                 urllib.request.HTTPCookieProcessor(self.cookies))
        self.csrf = None
        self.events = []
        self.output = output

    def request(self, method, path, body=None, *, expected=200, content_type='application/json'):
        if not re.fullmatch(r'/api/[a-zA-Z0-9_/?=&.-]+', path):
            raise ValueError('invalid API path')
        headers = {'Origin': 'http://127.0.0.1:4173'}
        if method != 'GET':
            if self.csrf is None:
                self.csrf = self.request('GET', '/api/auth/csrf')
            headers[self.csrf['headerName']] = self.csrf['token']
            headers['Content-Type'] = content_type
        encoded = body if isinstance(body, bytes) else (json.dumps(body).encode() if body is not None else None)
        if encoded and len(encoded) > MAX_BODY:
            raise ValueError('API request exceeds byte limit')
        request = urllib.request.Request('http://127.0.0.1:8080' + path, data=encoded, method=method, headers=headers)
        started = time.monotonic_ns()
        try:
            response = self.opener.open(request, timeout=120)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            data = response.read(MAX_BODY + 1)
            if len(data) > MAX_BODY:
                raise ValueError('API response exceeds byte limit')
            request_id = response.headers.get('X-Request-Id', '')
            self.events.append(dict(method=method, path=path, status=response.status,
                                    requestId=request_id, httpNs=time.monotonic_ns() - started))
            if response.status != expected:
                self.events[-1]['failureKind'] = failure_kind(data)
            if self.output is not None:
                (self.output / 'preparation-requests.json').write_text(json.dumps(self.events) + '\n')
            if response.status != expected:
                # Do not echo bodies containing auth grants or submitted credentials.
                raise RuntimeError(f'API {method} {path} returned {response.status}, expected {expected}')
            return json.loads(data)['data'] if data else None

    def login(self, credentials):
        self.request('POST', '/api/auth/login', dict(username=credentials['username'], password=credentials['initialPassword']))
        self.csrf = None
        self.request('POST', '/api/auth/password/change', dict(currentPassword=credentials['initialPassword'],
                     newPassword=credentials['password']), expected=204)
        self.cookies.clear()
        self.csrf = None
        self.request('POST', '/api/auth/login', dict(username=credentials['username'], password=credentials['password']))
        self.csrf = None

    def prepare(self, identity):
        slug = 'ci-business-' + identity
        problem = self.request('POST', '/api/admin/problems',
                               dict(slug=slug, title='CI A+B', difficulty='EASY', codeMode='ACM', languageId='cpp'), expected=201)
        problem_id = problem['id']
        base = f'/api/admin/problems/{problem_id}'
        problem = self.request('PATCH', base, dict(slug=slug, title='CI A+B', statementMarkdown='计算两个整数的和。',
            inputDescriptionMarkdown='输入两个整数。', outputDescriptionMarkdown='输出它们的和。',
            constraintsMarkdown=None, hintMarkdown=None, difficulty='EASY', tags=[],
            samples=[dict(ordinal=1, input='1 2', output='3', explanationMarkdown=None)],
            starterCode='', rowVersion=problem['rowVersion']))
        archive = fixture_zip()
        boundary = 'cherry-ci-' + secrets.token_hex(16)
        multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="cases.zip"\r\n'
                     'Content-Type: application/zip\r\n\r\n').encode() + archive + f'\r\n--{boundary}--\r\n'.encode()
        # 上传即替换：problem-service 把 ZIP 写成协议目录，节点按地址读取，没有“部署”这一步。
        data = self.request('PUT', base + '/test-data', multipart,
                            expected=200, content_type='multipart/form-data; boundary=' + boundary)
        if data['testcaseCount'] != 6 or len(data['digest']) != 64:
            raise ValueError('uploaded data identity mismatch')
        return dict(slug=slug, problemId=problem_id, testDataDigest=data['digest']), base

    def calibrate(self, base):
        calibration = self.request('POST', base + '/calibration', dict(languageId='cpp', cpuNs=1000000000,
            memoryBytes=268435456, clockNs=None, referenceSource=(FIXTURES / 'calibration.cpp').read_text()))
        if calibration['status'] != 'VALID':
            raise ValueError('new calibration did not become VALID')
        check = self.request('GET', base + '/publish-check')
        if check['ready'] is not True:
            raise ValueError('normal publication checks failed')
        problem = self.request('GET', base)
        self.request('POST', base + '/publish', dict(rowVersion=problem['rowVersion']))
        return calibration

    def make_public(self, context):
        # 题目没有版本：公开就是同一道题变成 PUBLIC，核对身份即可。
        base = '/api/admin/problems/' + context['problemId']
        problem = self.request('GET', base)
        if (problem['id'] != context['problemId'] or problem['slug'] != context['slug']
                or problem['visibility'] != 'PUBLIC' or problem['testData']['digest'] != context['testDataDigest']):
            raise ValueError('published problem identity mismatch')
        public = self.request('GET', '/api/problems/' + context['slug'])
        if any(public[key] != context[key] for key in ('problemId', 'slug')):
            raise ValueError('public problem identity mismatch')
