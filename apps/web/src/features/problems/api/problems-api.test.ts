import { http, HttpResponse } from 'msw';
import { expect, test } from 'vitest';

import { ApiError } from '@/lib/api/api-client';
import { server } from '@/test/mocks/server';

import { getProblem, listAdminProblems, listProblems } from './problems-api';

const requestId = 'req_problem_contract_test';
const problemId = '5f16b8c1-9c31-4d46-a2aa-9ba02cf65772';
const versionId = '454ef3b0-082e-4de6-a3d0-0f75d9a81137';
const canary = 'storageRef=s3://secret/reference-source.cpp';

function response(data: object, meta: object = {}) {
  return HttpResponse.json(
    { data, meta: { requestId, ...meta } },
    { headers: { 'X-Request-Id': requestId } },
  );
}

test('validates public list fields, accepts additive fields, and strips sensitive canaries', async () => {
  server.use(
    http.get('/api/problems', () =>
      response(
        {
          items: [
            {
              problemId,
              slug: 'two-sum',
              currentVersionId: versionId,
              versionNo: 1,
              title: '两数之和',
              difficulty: 'EASY',
              tags: ['array'],
              codeMode: 'ACM',
              allowedLanguages: [{ id: 'cpp', displayName: 'C++', storageRef: canary }],
              storageRef: canary,
            },
          ],
          futureField: true,
        },
        { pagination: { kind: 'cursor', nextCursor: null, hasMore: false } },
      ),
    ),
  );

  const result = await listProblems({ sort: 'UPDATED_DESC', size: 20 });

  expect(result.items[0]).not.toHaveProperty('storageRef');
  expect(result.items[0]?.allowedLanguages[0]).not.toHaveProperty('storageRef');
  expect(JSON.stringify(result)).not.toContain(canary);
});

test('rejects malformed required public detail fields as a contract error', async () => {
  server.use(
    http.get('/api/problems/two-sum', () =>
      response({ problemId, problemVersionId: versionId, slug: 'two-sum', title: '缺少字段' }),
    ),
  );

  const error = await getProblem('two-sum').catch((reason: unknown) => reason);
  expect(error).toBeInstanceOf(ApiError);
  expect(error).toMatchObject({ kind: 'contract', status: 200 });
});

test('omits UI-only admin defaults from the backend query', async () => {
  let requestUrl: URL | undefined;
  server.use(
    http.get('/api/admin/problems', ({ request }) => {
      requestUrl = new URL(request.url);
      return response(
        { items: [] },
        { pagination: { kind: 'page', page: 1, size: 20, totalElements: 0, totalPages: 0 } },
      );
    }),
  );

  await listAdminProblems('', 'ALL', 1);

  expect(requestUrl?.pathname).toBe('/api/admin/problems');
  expect(requestUrl?.searchParams.toString()).toBe('page=1&size=20');
});

test('keeps valid admin filters in the backend query', async () => {
  let requestUrl: URL | undefined;
  server.use(
    http.get('/api/admin/problems', ({ request }) => {
      requestUrl = new URL(request.url);
      return response(
        { items: [] },
        { pagination: { kind: 'page', page: 2, size: 20, totalElements: 0, totalPages: 0 } },
      );
    }),
  );

  await listAdminProblems('two sum', 'ARCHIVED', 2);

  expect(requestUrl?.searchParams.get('q')).toBe('two sum');
  expect(requestUrl?.searchParams.get('status')).toBe('ARCHIVED');
  expect(requestUrl?.searchParams.get('page')).toBe('2');
  expect(requestUrl?.searchParams.get('size')).toBe('20');
});
