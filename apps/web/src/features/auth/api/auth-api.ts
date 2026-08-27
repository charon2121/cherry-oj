import { z } from 'zod';

import type {
  AuthSessionData,
  ChangePasswordRequestWritable,
  LoginRequestWritable,
  UserAccountData,
} from '@/generated/api';
import { ApiError, requestJson, requestVoid } from '@/lib/api/api-client';
import { clearCsrfToken, withCsrf } from '@/lib/api/csrf';

const userAccountSchema = z.object({
  id: z.string().uuid(),
  username: z.string().regex(/^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$/),
  role: z.enum(['USER', 'ADMIN']),
  status: z.enum(['ACTIVE', 'DISABLED']),
  passwordChangeRequired: z.boolean(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  rowVersion: z.number().int().min(0),
}) satisfies z.ZodType<UserAccountData>;

const authenticatedSessionSchema = z.object({
  authenticated: z.literal(true),
  user: userAccountSchema,
});

const authSessionSchema = z.discriminatedUnion('authenticated', [
  z.object({ authenticated: z.literal(false) }),
  authenticatedSessionSchema,
]) satisfies z.ZodType<AuthSessionData>;

export async function getSession(signal?: AbortSignal): Promise<AuthSessionData> {
  try {
    return (
      await requestJson('/api/auth/session', authSessionSchema, {
        ...(signal === undefined ? {} : { signal }),
      })
    ).data;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      clearCsrfToken();
      return { authenticated: false };
    }
    throw error;
  }
}

export function login(request: LoginRequestWritable) {
  return withCsrf(
    async (token) =>
      (
        await requestJson('/api/auth/login', authenticatedSessionSchema, {
          method: 'POST',
          body: request,
          csrfToken: token,
        })
      ).data,
  );
}

export async function logout() {
  await withCsrf((token) => requestVoid('/api/auth/logout', { method: 'POST', csrfToken: token }));
  clearCsrfToken();
}

export async function changePassword(request: ChangePasswordRequestWritable) {
  await withCsrf((token) =>
    requestVoid('/api/auth/password/change', {
      method: 'POST',
      body: request,
      csrfToken: token,
    }),
  );
  clearCsrfToken();
}

export function clearCsrfForTests() {
  clearCsrfToken();
}
