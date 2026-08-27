import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, test } from 'vitest';

import { server } from '@/test/mocks/server';

import { clearCsrfForTests, getSession, login } from './auth-api';

const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';
const csrfToken = 'csrf-token-that-is-long-enough';
const user = {
  id: 'd0e35399-6487-4ac8-8138-8d5bd60eb003',
  username: 'alice',
  role: 'USER' as const,
  status: 'ACTIVE' as const,
  passwordChangeRequired: false,
  createdAt: '2026-08-26T01:00:00Z',
  updatedAt: '2026-08-26T01:00:00Z',
  rowVersion: 0,
};

function success(data: object, init: ResponseInit = {}) {
  return HttpResponse.json(
    { data, meta: { requestId } },
    {
      ...init,
      headers: { ...Object.fromEntries(new Headers(init.headers)), 'X-Request-Id': requestId },
    },
  );
}

function csrfRejected() {
  return HttpResponse.json(
    {
      type: 'urn:cherry-oj:problem:csrf-rejected',
      title: 'CSRF 校验失败',
      status: 403,
      code: 'CSRF_REJECTED',
      instance: `urn:cherry-oj:request:${requestId}`,
      meta: { requestId },
    },
    {
      status: 403,
      headers: { 'Content-Type': 'application/problem+json', 'X-Request-Id': requestId },
    },
  );
}

afterEach(() => {
  clearCsrfForTests();
});

describe('auth API', () => {
  test('maps a missing session to anonymous without exposing the problem', async () => {
    server.use(
      http.get('/api/auth/session', () =>
        HttpResponse.json(
          {
            type: 'urn:cherry-oj:problem:unauthenticated',
            title: '未登录',
            status: 401,
            code: 'UNAUTHENTICATED',
            instance: `urn:cherry-oj:request:${requestId}`,
            meta: { requestId },
          },
          {
            status: 401,
            headers: { 'Content-Type': 'application/problem+json', 'X-Request-Id': requestId },
          },
        ),
      ),
    );

    await expect(getSession()).resolves.toEqual({ authenticated: false });
  });

  test('uses one in-memory CSRF token, includes cookies, and strips unexpected token fields', async () => {
    let csrfCalls = 0;
    let loginCalls = 0;
    server.use(
      http.get('/api/auth/csrf', () => {
        csrfCalls += 1;
        return success({ token: csrfToken, headerName: 'X-CSRF-Token' });
      }),
      http.post('/api/auth/login', async ({ request }) => {
        loginCalls += 1;
        expect(request.credentials).toBe('include');
        expect(request.headers.get('X-CSRF-Token')).toBe(csrfToken);
        await expect(request.json()).resolves.toEqual({
          username: 'alice',
          password: 'secret-value',
        });
        return success({
          authenticated: true,
          user,
          accessToken: 'must-be-discarded',
          loginGrant: 'must-also-be-discarded',
        });
      }),
    );

    const result = await login({ username: 'alice', password: 'secret-value' });

    expect(result).toEqual({ authenticated: true, user });
    expect(result).not.toHaveProperty('accessToken');
    expect(result).not.toHaveProperty('loginGrant');
    expect(csrfCalls).toBe(1);
    expect(loginCalls).toBe(1);
  });

  test('refreshes CSRF once and retries the mutation once after rejection', async () => {
    let csrfCalls = 0;
    let loginCalls = 0;
    server.use(
      http.get('/api/auth/csrf', () => {
        csrfCalls += 1;
        return success({ token: `${csrfToken}-${csrfCalls}`, headerName: 'X-CSRF-Token' });
      }),
      http.post('/api/auth/login', ({ request }) => {
        loginCalls += 1;
        if (loginCalls === 1) {
          expect(request.headers.get('X-CSRF-Token')).toBe(`${csrfToken}-1`);
          return csrfRejected();
        }
        expect(request.headers.get('X-CSRF-Token')).toBe(`${csrfToken}-2`);
        return success({ authenticated: true, user });
      }),
    );

    await expect(login({ username: 'alice', password: 'secret-value' })).resolves.toEqual({
      authenticated: true,
      user,
    });
    expect(csrfCalls).toBe(2);
    expect(loginCalls).toBe(2);
  });
});
