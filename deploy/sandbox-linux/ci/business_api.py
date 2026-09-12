"""Normal authenticated preparation. Mutating requests are sent exactly once."""
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


def failure_kind(data):
    # Classify only exact, fixed public errors; never export an arbitrary response field.
    try:
        value = json.loads(data)
    except (ValueError, UnicodeError):
        return 'UNCLASSIFIED'
    if not isinstance(value, dict):
        return 'UNCLASSIFIED'
    if value.get('code') != 'SERVICE_UNAVAILABLE':
        return 'UNCLASSIFIED'
    return {'身份服务配置不一致，请联系管理员。': 'IDENTITY_CONFIGURATION_MISMATCH',
            '身份信任状态暂时不一致，请稍后重试。': 'IDENTITY_TRUST_MISMATCH',
            '服务暂时不可用，请稍后重试。': 'UPSTREAM_UNAVAILABLE'}.get(value.get('detail'), 'UNCLASSIFIED')


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
        problem_id, version_id = problem['id'], problem['versions'][0]['id']
        base = f'/api/admin/problems/{problem_id}/versions/{version_id}'
        version = self.request('GET', base)
        version = self.request('PATCH', base, dict(title='CI A+B', statementMarkdown='计算两个整数的和。',
            inputDescriptionMarkdown='输入两个整数。', outputDescriptionMarkdown='输出它们的和。',
            constraintsMarkdown=None, hintMarkdown=None, difficulty='EASY', tags=[],
            samples=[dict(ordinal=1, input='1 2', output='3', explanationMarkdown=None)],
            starterCode='', changeSummary=None, rowVersion=version['rowVersion']))
        archive = fixture_zip()
        boundary = 'cherry-ci-' + secrets.token_hex(16)
        multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="cases.zip"\r\n'
                     'Content-Type: application/zip\r\n\r\n').encode() + archive + f'\r\n--{boundary}--\r\n'.encode()
        asset = self.request('POST', f'/api/admin/problems/{problem_id}/test-data', multipart,
                             expected=201, content_type='multipart/form-data; boundary=' + boundary)
        if asset['status'] != 'READY' or asset['contentSha256'] != hashlib.sha256(archive).hexdigest():
            raise ValueError('uploaded data identity mismatch')
        version = self.request('PUT', base + '/test-data', dict(testDataVersionId=asset['id'], rowVersion=version['rowVersion']))
        deployment = self.request('POST', base + '/deployment', dict(testDataVersionId=asset['id'],
                                  expectedSha256=asset['contentSha256'], rowVersion=version['rowVersion']))
        if deployment['status'] != 'READY':
            raise ValueError('data was not deployed')
        return dict(slug=slug, problemId=problem_id, problemVersionId=version_id, testDataVersionId=asset['id'],
                    dataSha256=asset['contentSha256']), base

    def calibrate(self, base):
        version = self.request('GET', base)
        calibration = self.request('POST', base + '/calibration', dict(languageId='cpp', cpuNs=1000000000,
            memoryBytes=268435456, clockNs=None, referenceSource=(FIXTURES / 'calibration.cpp').read_text(),
            rowVersion=version['rowVersion']))
        if calibration['status'] != 'VALID':
            raise ValueError('new calibration did not become VALID')
        check = self.request('GET', base + '/publish-check')
        if check['ready'] is not True:
            raise ValueError('normal publication checks failed')
        version = self.request('GET', base)
        self.request('POST', base + '/publish', dict(rowVersion=version['rowVersion']))
        return calibration

    def make_public(self, context):
        # Publishing a version leaves the problem private; use the normal admin transition.
        base = '/api/admin/problems/' + context['problemId']
        problem = self.request('GET', base)
        if (problem['id'] != context['problemId'] or problem['slug'] != context['slug']
                or problem['currentPublishedVersionId'] != context['problemVersionId']):
            raise ValueError('published problem identity mismatch')
        self.request('PATCH', base, dict(slug=context['slug'], visibility='PUBLIC', rowVersion=problem['rowVersion']))
        public = self.request('GET', '/api/problems/' + context['slug'])
        if any(public[key] != context[key] for key in ('problemId', 'problemVersionId', 'slug')):
            raise ValueError('public problem identity mismatch')
