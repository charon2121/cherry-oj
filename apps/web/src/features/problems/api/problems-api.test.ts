import { http, HttpResponse } from 'msw';
import { expect, test } from 'vitest';

import { ApiError } from '@/lib/api/api-client';
import { server } from '@/test/mocks/server';

import {
  deleteProblem,
  getProblem,
  listAdminProblems,
  listProblems,
  replaceTestData,
  unpublish,
} from './problems-api';

const requestId = 'req_problem_contract_test';
const problemId = '5f16b8c1-9c31-4d46-a2aa-9ba02cf65772';
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
      response({ problemId, slug: 'two-sum', title: '缺少字段' }),
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

const digest = '6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4';
const csrf = http.get('/api/auth/csrf', () =>
  response({ token: 'csrf-token-for-problem-tests', headerName: 'X-CSRF-Token' }),
);

test('replaces the single test data with PUT and no version in the path', async () => {
  let method: string | undefined;
  let path: string | undefined;
  server.use(
    csrf,
    http.put(`/api/admin/problems/${problemId}/test-data`, ({ request }) => {
      method = request.method;
      path = new URL(request.url).pathname;
      return response({
        digest,
        testcaseCount: 2,
        totalBytes: 16,
        updatedAt: '2026-10-07T00:00:00',
        manifest: { testcaseCount: 2, totalBytes: 16, files: [] },
      });
    }),
  );

  const result = await replaceTestData(problemId, new File(['zip'], 'data.zip'));

  expect(method).toBe('PUT');
  expect(path).toBe(`/api/admin/problems/${problemId}/test-data`);
  expect(result).toMatchObject({ digest, testcaseCount: 2 });
});

test('unpublish and delete address the problem itself', async () => {
  const seen: string[] = [];
  server.use(
    csrf,
    http.post(`/api/admin/problems/${problemId}/unpublish`, ({ request }) => {
      seen.push(`${request.method} ${new URL(request.url).pathname}`);
      return response({
        id: problemId,
        slug: 'two-sum',
        visibility: 'PRIVATE',
        status: 'ACTIVE',
        codeMode: 'ACM',
        title: '两数之和',
        statementMarkdown: 's',
        inputDescriptionMarkdown: 'i',
        outputDescriptionMarkdown: 'o',
        constraintsMarkdown: null,
        hintMarkdown: null,
        difficulty: 'EASY',
        tags: [],
        samples: [],
        allowedLanguages: [{ id: 'cpp', displayName: 'C++', starterCode: '' }],
        testData: null,
        createdAt: '2026-10-07T00:00:00',
        updatedAt: '2026-10-07T00:00:00',
        publishedAt: '2026-10-07T00:00:00',
        rowVersion: 4,
      });
    }),
    http.delete(`/api/admin/problems/${problemId}`, ({ request }) => {
      const url = new URL(request.url);
      seen.push(`${request.method} ${url.pathname}?${url.searchParams.toString()}`);
      return new HttpResponse(null, { status: 204, headers: { 'X-Request-Id': requestId } });
    }),
  );

  await unpublish(problemId, 3);
  await deleteProblem(problemId, 4);

  expect(seen).toEqual([
    `POST /api/admin/problems/${problemId}/unpublish`,
    `DELETE /api/admin/problems/${problemId}?rowVersion=4`,
  ]);
});
