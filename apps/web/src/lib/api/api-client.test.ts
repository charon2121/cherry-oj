import { delay, http, HttpResponse } from 'msw';
import { expect, test } from 'vitest';
import { z } from 'zod';

import { server } from '@/test/mocks/server';

import { ApiError, requestJson, requestVoid } from './api-client';

const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';
const itemSchema = z.object({ id: z.string() }).loose();

test('accepts additive response fields and returns validated data and meta', async () => {
  server.use(
    http.get('/api/test-item', () =>
      HttpResponse.json(
        {
          data: { id: 'item-1', futureDataField: true },
          meta: { requestId, futureMetaField: true },
          futureEnvelopeField: true,
        },
        { headers: { 'X-Request-Id': requestId } },
      ),
    ),
  );

  const result = await requestJson('/api/test-item', itemSchema);

  expect(result.data.id).toBe('item-1');
  expect(result.meta.requestId).toBe(requestId);
  expect(result).toMatchObject({ status: 200, location: undefined });
});

test('validates cursor pagination while tolerating additive fields', async () => {
  server.use(
    http.get('/api/test-items', () =>
      HttpResponse.json(
        {
          data: { items: [{ id: 'item-1' }] },
          meta: {
            requestId,
            pagination: {
              kind: 'cursor',
              nextCursor: 'opaque-next',
              hasMore: true,
              futurePaginationField: true,
            },
          },
        },
        { headers: { 'X-Request-Id': requestId } },
      ),
    ),
  );

  const result = await requestJson(
    '/api/test-items',
    z.object({ items: z.array(itemSchema) }).loose(),
  );

  expect(result.meta.pagination).toMatchObject({
    kind: 'cursor',
    nextCursor: 'opaque-next',
    hasMore: true,
  });
});

test.each([
  { status: 201, path: '/api/test-created' as const },
  { status: 202, path: '/api/test-accepted' as const },
])('preserves HTTP $status and Location for creation workflows', async ({ status, path }) => {
  server.use(
    http.post(path, () =>
      HttpResponse.json(
        { data: { id: 'item-1' }, meta: { requestId } },
        {
          status,
          headers: { Location: '/api/test-item/item-1', 'X-Request-Id': requestId },
        },
      ),
    ),
  );

  const result = await requestJson(path, itemSchema, { method: 'POST', body: { name: 'item' } });

  expect(result).toMatchObject({ status, location: '/api/test-item/item-1' });
});

test('rejects creation responses without Location', async () => {
  server.use(
    http.post('/api/test-created', () =>
      HttpResponse.json(
        { data: { id: 'item-1' }, meta: { requestId } },
        { status: 201, headers: { 'X-Request-Id': requestId } },
      ),
    ),
  );

  await expect(
    requestJson('/api/test-created', itemSchema, { method: 'POST', body: {} }),
  ).rejects.toMatchObject({ kind: 'contract', status: 201 });
});

test('rejects arrays before sending a JSON request body', async () => {
  await expect(
    requestJson('/api/test-item', itemSchema, { method: 'POST', body: [] }),
  ).rejects.toMatchObject({ kind: 'contract', status: undefined });
});

test('classifies an unserializable request body as a contract failure', async () => {
  const circular: Record<string, unknown> = {};
  circular.self = circular;

  await expect(
    requestJson('/api/test-item', itemSchema, { method: 'POST', body: circular }),
  ).rejects.toMatchObject({ kind: 'contract', status: undefined });
});

test('maps a valid RFC 9457 response to a typed HTTP error', async () => {
  server.use(
    http.post('/api/test-item', () =>
      HttpResponse.json(
        {
          type: 'urn:cherry-oj:problem:validation-failed',
          title: '请求参数校验失败',
          status: 422,
          detail: '请检查标记字段。',
          instance: `urn:cherry-oj:request:${requestId}`,
          code: 'VALIDATION_FAILED',
          meta: { requestId },
          violations: [{ path: 'title', code: 'NOT_BLANK', message: '标题不能为空。' }],
        },
        {
          status: 422,
          headers: {
            'Content-Type': 'application/problem+json',
            'Retry-After': '3',
            'X-Request-Id': requestId,
          },
        },
      ),
    ),
  );

  const error = await requestJson('/api/test-item', itemSchema, {
    method: 'POST',
    body: { title: '' },
  }).catch((reason: unknown) => reason);

  expect(error).toBeInstanceOf(ApiError);
  expect(error).toMatchObject({
    kind: 'http',
    status: 422,
    code: 'VALIDATION_FAILED',
    requestId,
    retryAfterNs: 3_000_000_000n,
    message: '请检查标记字段。',
  });
});

test('rejects mismatched request IDs as a contract failure', async () => {
  server.use(
    http.get('/api/test-item', () =>
      HttpResponse.json(
        { data: { id: 'item-1' }, meta: { requestId } },
        { headers: { 'X-Request-Id': 'req_0000000000000000' } },
      ),
    ),
  );

  await expect(requestJson('/api/test-item', itemSchema)).rejects.toMatchObject({
    kind: 'contract',
    status: 200,
  });
});

test('handles 204 as the explicit bodyless exception', async () => {
  server.use(
    http.delete(
      '/api/test-item',
      () => new HttpResponse(null, { status: 204, headers: { 'X-Request-Id': requestId } }),
    ),
  );

  await expect(requestVoid('/api/test-item', { method: 'DELETE' })).resolves.toEqual({
    requestId,
  });
});

test('classifies non-JSON upstream responses as contract failures', async () => {
  server.use(
    http.get(
      '/api/test-item',
      () =>
        new HttpResponse('<html>proxy error</html>', {
          status: 502,
          headers: { 'Content-Type': 'text/html', 'X-Request-Id': requestId },
        }),
    ),
  );

  await expect(requestJson('/api/test-item', itemSchema)).rejects.toMatchObject({
    kind: 'contract',
    status: 502,
  });
});

test('preserves caller cancellation without inventing an HTTP status', async () => {
  const controller = new AbortController();
  controller.abort();

  await expect(
    requestJson('/api/test-item', itemSchema, { signal: controller.signal }),
  ).rejects.toMatchObject({ kind: 'aborted', status: undefined });
});

test('classifies AbortSignal timeouts separately from cancellation', async () => {
  server.use(
    http.get('/api/test-item', async () => {
      await delay(50);
      return HttpResponse.json(
        { data: { id: 'late' }, meta: { requestId } },
        { headers: { 'X-Request-Id': requestId } },
      );
    }),
  );

  await expect(
    requestJson('/api/test-item', itemSchema, { signal: AbortSignal.timeout(1) }),
  ).rejects.toMatchObject({ kind: 'timeout', status: undefined });
});
