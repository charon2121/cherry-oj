import { expect, test } from 'vitest';

import { ApiError } from '@/lib/api/api-client';

import { authErrorMessage } from './auth-error-message';

test('shows the server retry delay for rate-limited login', () => {
  const error = new ApiError('rate limited', {
    kind: 'http',
    status: 429,
    code: 'RATE_LIMITED',
    retryAfterNs: 3_200_000_000n,
  });

  expect(authErrorMessage(error)).toBe('尝试次数过多，请 4 秒后再试。');
});
