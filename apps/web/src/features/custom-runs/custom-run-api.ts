import { z } from 'zod';

import type { CustomRunData, CustomRunRequest } from '@/generated/api';
import { ApiError, requestJson } from '@/lib/api/api-client';

const bytes = (limit: number) =>
  z.string().refine((value) => new TextEncoder().encode(value).length <= limit);
const output = z
  .object({
    text: bytes(16384),
    capturedBytes: z.number().int().nonnegative(),
    truncated: z.boolean(),
  })
  .refine((v) => v.capturedBytes >= new TextEncoder().encode(v.text).length);
export const runSchema = z
  .object({
    problemId: z.string().uuid(),
    problemVersionId: z.string().uuid(),
    problemVersionNo: z.number().int().positive(),
    languageId: z.literal('cpp'),
    status: z.enum([
      'COMPLETED',
      'COMPILE_ERROR',
      'RUNTIME_ERROR',
      'TIME_LIMIT_EXCEEDED',
      'MEMORY_LIMIT_EXCEEDED',
      'OUTPUT_LIMIT_EXCEEDED',
    ]),
    cpuNs: z.number().int().nonnegative().optional(),
    memoryBytes: z.number().int().nonnegative().optional(),
    stdout: output.optional(),
    stderr: output.optional(),
    compileDiagnostic: bytes(8192).optional(),
    effectiveLimits: z.object({
      cpuNs: z.number().int().nonnegative(),
      memoryBytes: z.number().int().nonnegative(),
      clockNs: z.number().int().nonnegative(),
    }),
  })
  .refine((v) =>
    v.status === 'COMPILE_ERROR'
      ? v.compileDiagnostic !== undefined &&
        v.stdout === undefined &&
        v.stderr === undefined &&
        v.cpuNs === undefined &&
        v.memoryBytes === undefined
      : v.stdout !== undefined &&
        v.stderr !== undefined &&
        v.cpuNs !== undefined &&
        v.memoryBytes !== undefined &&
        v.compileDiagnostic === undefined,
  )
  .transform(
    (v) =>
      Object.fromEntries(
        Object.entries(v).filter(([, value]) => value !== undefined),
      ) as CustomRunData,
  );

export async function createCustomRun(body: CustomRunRequest, userId: string, signal: AbortSignal) {
  // Obtain CSRF before POST. An uncertain execution must never be retried automatically.
  const csrf = await requestJson(
    '/api/auth/csrf',
    z.object({ token: z.string().min(16).max(512), headerName: z.literal('X-CSRF-Token') }),
    { signal },
  );
  const { data } = await requestJson('/api/custom-runs', runSchema, {
    method: 'POST',
    body,
    expectedUserId: userId,
    csrfToken: csrf.data.token,
    signal,
  });
  if (data.problemId !== body.problemId || data.problemVersionId !== body.expectedProblemVersionId)
    throw new ApiError('运行结果与当前请求版本不一致。', { kind: 'contract' });
  return data;
}
