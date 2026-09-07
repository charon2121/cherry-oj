import { z } from 'zod';

import type { CreateSubmissionRequest, SubmissionData } from '@/generated/api';
import { requestJson } from '@/lib/api/api-client';
import { withCsrf } from '@/lib/api/csrf';

export const submissionSchema = z
  .object({
    id: z.string().uuid(),
    problemId: z.string().uuid(),
    problemVersionId: z.string().uuid(),
    problemVersionNo: z.number().int().positive(),
    problemTitle: z.string(),
    languageId: z.literal('cpp'),
    status: z.enum(['PENDING', 'JUDGING', 'DONE']),
    createdAt: z.string().datetime(),
    verdict: z.enum(['AC', 'WA', 'PE', 'CE', 'RE', 'TLE', 'MLE', 'OLE', 'SE']).optional(),
    cpuNs: z.number().int().nonnegative().optional(),
    memoryBytes: z.number().int().nonnegative().optional(),
    passedCount: z.number().int().nonnegative().optional(),
    executedCount: z.number().int().nonnegative().optional(),
    totalCount: z.number().int().positive().optional(),
    message: z.string().max(8192).optional(),
    finishedAt: z.string().datetime().optional(),
  })
  .refine((value) => value.status !== 'DONE' || value.verdict !== undefined)
  .transform(
    (value) =>
      Object.fromEntries(
        Object.entries(value).filter(([, field]) => field !== undefined),
      ) as SubmissionData,
  ) satisfies z.ZodType<SubmissionData>;

export async function createSubmission(
  key: string,
  body: CreateSubmissionRequest,
  expectedUserId: string,
) {
  return withCsrf(
    async (token) =>
      (
        await requestJson('/api/submissions', submissionSchema, {
          method: 'POST',
          body,
          idempotencyKey: key,
          expectedUserId,
          csrfToken: token,
          signal: AbortSignal.timeout(25_000),
        })
      ).data,
  );
}

export async function getSubmission(id: string, signal: AbortSignal) {
  return (
    await requestJson(`/api/submissions/${encodeURIComponent(id)}`, submissionSchema, { signal })
  ).data;
}

export async function findSubmission(key: string, signal: AbortSignal) {
  return (
    await requestJson(`/api/submission-requests/${encodeURIComponent(key)}`, submissionSchema, {
      signal,
    })
  ).data;
}
