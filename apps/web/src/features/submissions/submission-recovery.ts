import { z } from 'zod';

const source = z.string().refine((value) => new TextEncoder().encode(value).length <= 262144);
export const recoverySchema = z.object({
  key: z.string().uuid(),
  body: z.object({
    problemId: z.string().uuid(),
    expectedProblemVersionId: z.string().uuid(),
    languageId: z.literal('cpp'),
    source,
  }),
  submissionId: z.string().uuid().optional(),
});
export type SubmissionRecovery = z.infer<typeof recoverySchema>;
export function recoveryKey(userId: string, problemId: string) {
  return `cherry-oj.submission.v1:${userId}:${problemId}:cpp`;
}
export function loadRecovery(key: string): SubmissionRecovery | undefined {
  const value = localStorage.getItem(key);
  if (!value) return undefined;
  const result = recoverySchema.safeParse(JSON.parse(value) as unknown);
  if (!result.success)
    throw new Error('本机提交恢复记录无法读取，请保留当前代码并通过原提交链接查询。');
  return result.data;
}

// Protocol identity, never a DOM id. Create once per explicit new submission.
export function newSubmissionKey() {
  return crypto.randomUUID();
}
