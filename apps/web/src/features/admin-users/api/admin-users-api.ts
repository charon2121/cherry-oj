import { z } from 'zod';

import type {
  CreateUserDataWritable,
  CreateUserRequest,
  ResetUserPasswordDataWritable,
  ResetUserPasswordRequest,
  UpdateUserStatusRequest,
  UserAccountData,
} from '@/generated/api';
import { requestJson } from '@/lib/api/api-client';
import { withCsrf } from '@/lib/api/csrf';

const userSchema = z.object({
  id: z.string().uuid(),
  username: z.string(),
  role: z.enum(['USER', 'ADMIN']),
  status: z.enum(['ACTIVE', 'DISABLED']),
  passwordChangeRequired: z.boolean(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  rowVersion: z.number().int().min(0),
}) satisfies z.ZodType<UserAccountData>;

const userListSchema = z.object({ items: z.array(userSchema).max(100) });
const createdUserSchema = z.object({
  user: userSchema,
  temporaryPassword: z.string().min(12).max(128),
}) satisfies z.ZodType<CreateUserDataWritable>;
const resetPasswordSchema = z.object({
  temporaryPassword: z.string().min(12).max(128),
}) satisfies z.ZodType<ResetUserPasswordDataWritable>;

export const adminUserKeys = {
  all: ['admin-users'] as const,
  list: (page: number, size: number) => [...adminUserKeys.all, 'list', page, size] as const,
};

export async function listUsers(page: number, size: number, signal?: AbortSignal) {
  const response = await requestJson(`/api/admin/users?page=${page}&size=${size}`, userListSchema, {
    ...(signal === undefined ? {} : { signal }),
  });
  const pagination = response.meta.pagination;
  if (pagination?.kind !== 'page') throw new Error('用户列表缺少分页信息。');
  return { items: response.data.items, pagination };
}

export function createUser(request: CreateUserRequest) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson('/api/admin/users', createdUserSchema, {
          method: 'POST',
          body: request,
          csrfToken,
        })
      ).data,
  );
}

export function updateUserStatus(userId: string, request: UpdateUserStatusRequest) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(`/api/admin/users/${userId}/status`, userSchema, {
          method: 'PATCH',
          body: request,
          csrfToken,
        })
      ).data,
  );
}

export function resetUserPassword(userId: string, request: ResetUserPasswordRequest) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(`/api/admin/users/${userId}/password-reset`, resetPasswordSchema, {
          method: 'POST',
          body: request,
          csrfToken,
        })
      ).data,
  );
}
