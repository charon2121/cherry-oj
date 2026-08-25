import { expect, test } from 'vitest';

import { ApiError } from '@/lib/api/api-client';

import { shouldRetryQuery } from './query-client';

test('only retries a query once for transient transport and server failures', () => {
  expect(shouldRetryQuery(0, new ApiError('offline', { kind: 'network' }))).toBe(true);
  expect(shouldRetryQuery(0, new ApiError('timeout', { kind: 'timeout' }))).toBe(true);
  expect(shouldRetryQuery(0, new ApiError('unavailable', { kind: 'http', status: 503 }))).toBe(
    true,
  );
  expect(shouldRetryQuery(1, new ApiError('offline', { kind: 'network' }))).toBe(false);
});

test('does not retry aborted, contract, or client HTTP failures', () => {
  expect(shouldRetryQuery(0, new ApiError('cancelled', { kind: 'aborted' }))).toBe(false);
  expect(shouldRetryQuery(0, new ApiError('invalid', { kind: 'contract' }))).toBe(false);
  expect(shouldRetryQuery(0, new ApiError('forbidden', { kind: 'http', status: 403 }))).toBe(false);
});
