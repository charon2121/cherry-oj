import { z } from 'zod';

import { ApiError, requestJson } from './api-client';

const csrfSchema = z.object({
  token: z.string().min(16).max(512),
  headerName: z.literal('X-CSRF-Token'),
});

let csrfToken: string | undefined;
let csrfRequest: Promise<string> | undefined;

async function loadCsrfToken() {
  if (csrfToken !== undefined) return csrfToken;
  csrfRequest ??= requestJson('/api/auth/csrf', csrfSchema).then((response) => {
    csrfToken = response.data.token;
    return csrfToken;
  });
  try {
    return await csrfRequest;
  } finally {
    csrfRequest = undefined;
  }
}

export async function withCsrf<T>(operation: (token: string) => Promise<T>) {
  const token = await loadCsrfToken();
  try {
    return await operation(token);
  } catch (error) {
    if (error instanceof ApiError && error.code === 'CSRF_REJECTED') {
      clearCsrfToken();
      return operation(await loadCsrfToken());
    }
    throw error;
  }
}

export function clearCsrfToken() {
  csrfToken = undefined;
  csrfRequest = undefined;
}
