import type { QueryClient } from '@tanstack/react-query';
import { z } from 'zod';

import { authKeys } from '@/features/auth/api/session-query';
import type { AuthSessionData, SubmissionSourceData } from '@/generated/api';
import { ApiError, requestJson } from '@/lib/api/api-client';

import { submissionSchema } from './submissions-api';

export const verdictSchema = z.enum(['AC', 'WA', 'PE', 'CE', 'RE', 'TLE', 'MLE', 'OLE', 'SE']);
export const historySearchSchema = z.object({
  submissionId: z.string().uuid().optional().catch(undefined),
  tab: z.enum(['statement', 'submissions']).optional().catch(undefined),
  historyPage: z.coerce.number().int().min(1).max(2147483647).optional().catch(undefined),
  historyVerdict: verdictSchema.optional().catch(undefined),
  historySubmissionId: z.string().uuid().optional().catch(undefined),
});
export type HistorySearch = z.infer<typeof historySearchSchema>;
export const historyKeys = {
  all: (userId: string, problemId: string) => ['submission-history', userId, problemId] as const,
};
export const sourceSchema = z.object({
  submissionId: z.string().uuid(),
  problemId: z.string().uuid(),
  problemVersionId: z.string().uuid(),
  languageId: z.literal('cpp'),
  source: z
    .string()
    .max(262144)
    .refine((source) => new TextEncoder().encode(source).length <= 262144),
}) satisfies z.ZodType<SubmissionSourceData>;

export function isPrivateAccessError(error: unknown) {
  return (
    error instanceof ApiError &&
    ([401, 403, 404].includes(error.status ?? 0) || error.code === 'SESSION_CHANGED')
  );
}

// The server checks the expected account; also reject a response if the UI identity changed in flight.
export async function withHistoryIdentity<T>(
  client: QueryClient,
  userId: string,
  read: () => Promise<T>,
) {
  const check = () => {
    const session = client.getQueryData<AuthSessionData>(authKeys.session());
    if (
      !session?.authenticated ||
      session.user.id !== userId ||
      session.user.status !== 'ACTIVE' ||
      session.user.passwordChangeRequired
    ) {
      throw new ApiError('登录状态已变化，请重新打开提交记录。', {
        kind: 'aborted',
        code: 'SESSION_CHANGED',
      });
    }
  };
  check();
  try {
    const value = await read();
    check();
    return value;
  } catch (error) {
    if (isPrivateAccessError(error) && !(error instanceof ApiError && error.status === 404)) {
      void client.invalidateQueries({ queryKey: authKeys.session() });
    }
    throw error;
  }
}

export async function listProblemSubmissions(
  userId: string,
  problemId: string,
  search: HistorySearch,
  signal: AbortSignal,
) {
  const params = new URLSearchParams({
    problemId,
    page: String(search.historyPage ?? 1),
    size: '20',
  });
  if (search.historyVerdict) params.set('verdict', search.historyVerdict);
  const result = await requestJson(
    `/api/submissions?${params}`,
    z.array(submissionSchema).max(20),
    { signal, expectedUserId: userId },
  );
  const pagination = result.meta.pagination;
  if (
    !pagination ||
    pagination.kind !== 'page' ||
    pagination.page !== (search.historyPage ?? 1) ||
    pagination.size !== 20 ||
    result.data.some((row) => row.problemId !== problemId)
  ) {
    throw new ApiError('提交记录响应不符合查询条件。', { kind: 'contract' });
  }
  return { items: result.data, pagination };
}

export async function getHistorySource(
  userId: string,
  problemId: string,
  id: string,
  signal: AbortSignal,
) {
  const { data } = await requestJson(
    `/api/submissions/${encodeURIComponent(id)}/source`,
    sourceSchema,
    { signal, expectedUserId: userId },
  );
  if (data.submissionId !== id || data.problemId !== problemId) {
    throw new ApiError('该提交不属于当前题目，请返回本题提交记录。', {
      kind: 'contract',
      status: 404,
    });
  }
  return data;
}
