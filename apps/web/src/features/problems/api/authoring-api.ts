import { z } from 'zod';

import type {
  ProblemAuthoringApplyRequest,
  ProblemAuthoringProposalRequest,
} from '@/generated/api';
import { requestJson } from '@/lib/api/api-client';
import { withCsrf } from '@/lib/api/csrf';

import { testDataSchema } from './problems-api';

const id = z.string().uuid();
const languageId = z.string().regex(/^[a-z][a-z0-9-]{0,31}$/);
const caseName = z.string().regex(/^[a-z][a-z0-9-]{0,62}$/);
const digest = z.string().regex(/^[a-f0-9]{64}$/);
const source = z.string().min(1).max(65_536);
const limits = z.strictObject({
  cpuNs: z.number().int().min(1).max(10_000_000_000),
  clockNs: z.number().int().min(1).max(120_000_000_000),
  memoryBytes: z.number().int().min(1).max(1_073_741_824),
});

export const authoringPlanSchema = z.strictObject({
  generatorSource: source,
  validatorSource: source,
  oracleSource: source,
  answerLanguageId: languageId,
  cases: z
    .array(
      z.strictObject({
        name: caseName,
        generatorInput: z.string().max(65_536),
        oracle: z.boolean(),
        expectedOutput: source.optional(),
        sampleOrdinal: z.number().int().min(1).max(100).optional(),
      }),
    )
    .min(1)
    .max(64),
  languages: z
    .array(
      z.strictObject({
        languageId,
        limits,
        referenceSource: source,
        wrongSolutions: z
          .array(z.strictObject({ source, witnessCaseName: caseName }))
          .min(1)
          .max(16),
      }),
    )
    .min(1)
    .max(4),
});

const stage = z.enum([
  'INPUT',
  'GENERATOR',
  'VALIDATOR',
  'ANSWERS',
  'ORACLE',
  'LANGUAGES',
  'EXPORT',
]);
const summary = z.object({
  id,
  kind: z.enum(['BUILD', 'PROPOSAL']),
  status: z.enum(['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED']),
  stage: stage.or(z.enum(['QUEUED', 'MODEL', 'COMPLETE'])),
  rowVersion: z.number().int(),
  attempt: z.number().int(),
  createdAt: z.string(),
  updatedAt: z.string(),
  appliedAt: z.string().nullable(),
});

const proposal = z.object({
  title: z.string(),
  statementMarkdown: z.string(),
  inputDescriptionMarkdown: z.string(),
  outputDescriptionMarkdown: z.string(),
  constraintsMarkdown: z.string(),
  samples: z
    .array(
      z.object({
        ordinal: z.number().int(),
        input: z.string(),
        output: z.string(),
        explanationMarkdown: z.string().nullable(),
      }),
    )
    .min(1),
  languages: z
    .array(
      z.object({ id: languageId, starterCode: z.string(), judgeTemplate: z.string().nullable() }),
    )
    .min(1),
  input: authoringPlanSchema,
});

const detail = z.object({
  job: summary,
  buildResult: z
    .object({
      schemaVersion: z.literal(1),
      status: z.enum(['SUCCEEDED', 'FAILED']),
      stage,
      testDataDigest: digest.optional(),
      testcaseCount: z.number().int().optional(),
      languages: z.array(
        z.object({
          languageId,
          status: z.enum(['PASSED', 'FAILED', 'NOT_RUN']),
          verdict: z.string().optional(),
          starterVerdict: z.string().optional(),
          toolchainDigest: digest.optional(),
          maxCpuNs: z.number().int().optional(),
          maxMemoryBytes: z.number().int().optional(),
          detail: z.string().optional(),
        }),
      ),
      detail: z.string().optional(),
    })
    .nullable(),
  proposal: proposal.nullable(),
  errorCode: z
    .enum([
      'AUTHORING_RUNNER_UNAVAILABLE',
      'AUTHORING_BUILD_FAILED',
      'AUTHORING_MODEL_UNAVAILABLE',
      'AUTHORING_MODEL_INVALID_RESULT',
      'AUTHORING_TIMEOUT',
      'AUTHORING_INTERRUPTED',
    ])
    .nullable(),
  reportAvailable: z.boolean(),
});

export type AuthoringPlan = z.infer<typeof authoringPlanSchema>;
export type AuthoringProposal = z.infer<typeof proposal>;
export type AuthoringJob = z.infer<typeof summary>;
export type AuthoringJobDetail = z.infer<typeof detail>;
export type CreateAuthoringJob =
  | ProblemAuthoringProposalRequest
  | {
      kind: 'BUILD';
      rowVersion: number;
      input: AuthoringPlan;
    };

const jobsPath = (problemId: string) =>
  `/api/admin/problems/${encodeURIComponent(problemId)}/authoring/jobs` as const;
const jobPath = (problemId: string, jobId: string) =>
  `${jobsPath(problemId)}/${encodeURIComponent(jobId)}` as const;
export const authoringReportPath = (problemId: string, jobId: string) =>
  `${jobPath(problemId, jobId)}/report`;

export async function listAuthoringJobs(problemId: string, signal: AbortSignal) {
  return (await requestJson(jobsPath(problemId), z.object({ items: z.array(summary) }), { signal }))
    .data.items;
}

export async function getAuthoringJob(problemId: string, jobId: string, signal: AbortSignal) {
  return (await requestJson(jobPath(problemId, jobId), detail, { signal })).data;
}

export function createAuthoringJob(problemId: string, body: CreateAuthoringJob) {
  return withCsrf(
    async (csrfToken) =>
      (await requestJson(jobsPath(problemId), detail, { method: 'POST', body, csrfToken })).data,
  );
}

export function changeAuthoringJob(problemId: string, jobId: string, action: 'retry' | 'cancel') {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(`${jobPath(problemId, jobId)}/${action}`, detail, {
          method: 'POST',
          csrfToken,
        })
      ).data,
  );
}

export function applyAuthoringBuild(
  problemId: string,
  jobId: string,
  body: ProblemAuthoringApplyRequest,
) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(`${jobPath(problemId, jobId)}/apply`, testDataSchema, {
          method: 'POST',
          body,
          csrfToken,
        })
      ).data,
  );
}
