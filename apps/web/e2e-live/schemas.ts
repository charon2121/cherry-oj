import { z } from 'zod';

// Runtime validation of the fields consumed by live assertions; no trust in JSON casts.
const id = z.string().uuid();
const counter = z.number().int().nonnegative();
const meta = z.object({ requestId: z.string().min(1).max(128) });
const output = z.object({
  text: z.string().max(16_384),
  capturedBytes: counter,
  truncated: z.boolean(),
});
export const runResponse = z.object({
  meta,
  data: z.object({
    problemId: id,
    problemVersionId: id,
    languageId: z.literal('cpp'),
    status: z.enum([
      'COMPLETED',
      'COMPILE_ERROR',
      'RUNTIME_ERROR',
      'TIME_LIMIT_EXCEEDED',
      'MEMORY_LIMIT_EXCEEDED',
      'OUTPUT_LIMIT_EXCEEDED',
    ]),
    cpuNs: counter.optional(),
    memoryBytes: counter.optional(),
    stdout: output.optional(),
    stderr: output.optional(),
    compileDiagnostic: z.string().max(16_384).optional(),
    effectiveLimits: z.object({ cpuNs: counter, memoryBytes: counter, clockNs: counter }),
  }),
});
export const submissionResponse = z.object({
  meta,
  data: z.object({
    id,
    problemId: id,
    problemVersionId: id,
    status: z.enum(['PENDING', 'JUDGING', 'DONE']),
    verdict: z.enum(['AC', 'WA', 'PE', 'TLE', 'MLE', 'OLE', 'RE', 'CE', 'SE']).optional(),
    passedCount: counter.optional(),
    executedCount: counter.optional(),
    totalCount: counter.optional(),
  }),
});
export const historyResponse = z.object({
  meta,
  data: z.object({
    submissionId: id,
    problemId: id,
    problemVersionId: id,
    source: z.string().max(262_144),
  }),
});
