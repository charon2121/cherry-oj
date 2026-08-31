import { queryOptions } from '@tanstack/react-query';
import { z } from 'zod';

import type {
  AdminProblem,
  AdminProblemVersion,
  BindTestDataRequest,
  CalibrateProblemRequestWritable,
  CreateProblemRequest,
  CreateProblemRevisionRequest,
  DeployTestDataRequest,
  ProblemDetail,
  ProblemStatus,
  ProblemSummary,
  PublishCheck,
  TestDataDeployment,
  TestDataVersion,
  UpdateProblemRequest,
  UpdateProblemVersionRequest,
} from '@/generated/api';
import { requestJson, requestMultipart, requestVoid } from '@/lib/api/api-client';
import { withCsrf } from '@/lib/api/csrf';

const id = z.string().uuid();
const date = z.string().min(1);
const sampleSchema = z
  .object({
    ordinal: z.number().int().min(1),
    input: z.string(),
    output: z.string(),
    explanationMarkdown: z.string().nullable(),
  })
  .strip();
const languageSummarySchema = z.object({ id: z.string(), displayName: z.string() }).strip();
const problemSummarySchema = z
  .object({
    problemId: id,
    slug: z.string(),
    currentVersionId: id,
    versionNo: z.number().int().min(1),
    title: z.string(),
    difficulty: z.enum(['UNRATED', 'EASY', 'MEDIUM', 'HARD']),
    tags: z.array(z.string()),
    codeMode: z.enum(['ACM', 'CORE']),
    allowedLanguages: z.array(languageSummarySchema),
  })
  .strip() satisfies z.ZodType<ProblemSummary>;
const problemDetailSchema = z
  .object({
    problemId: id,
    problemVersionId: id,
    versionNo: z.number().int().min(1),
    slug: z.string(),
    codeMode: z.enum(['ACM', 'CORE']),
    title: z.string(),
    difficulty: z.enum(['UNRATED', 'EASY', 'MEDIUM', 'HARD']),
    tags: z.array(z.string()),
    statementMarkdown: z.string(),
    inputDescriptionMarkdown: z.string(),
    outputDescriptionMarkdown: z.string(),
    constraintsMarkdown: z.string().nullable(),
    hintMarkdown: z.string().nullable(),
    samples: z.array(sampleSchema),
    allowedLanguages: z.array(languageSummarySchema.extend({ starterCode: z.string() }).strip()),
  })
  .strip() satisfies z.ZodType<ProblemDetail>;
const testDataSchema = z
  .object({
    id,
    problemId: id,
    status: z.enum(['UPLOADING', 'READY', 'FAILED']),
    sourceType: z.literal('MANUAL_UPLOAD'),
    contentSha256: z.string().nullable(),
    caseCount: z.number().int().nullable(),
    totalBytes: z.number().int().nullable(),
    manifest: z
      .object({
        caseCount: z.number().int(),
        totalBytes: z.number().int(),
        files: z.array(
          z.object({ name: z.string(), sizeBytes: z.number().int(), sha256: z.string() }).loose(),
        ),
      })
      .loose()
      .nullable(),
    createdAt: date,
    readyAt: date.nullable(),
    errorMessage: z.string().nullable(),
  })
  .loose() satisfies z.ZodType<TestDataVersion>;
const versionSummarySchema = z
  .object({
    id,
    versionNo: z.number().int(),
    status: z.enum(['DRAFT', 'VALIDATING', 'READY_FOR_REVIEW', 'PUBLISHED', 'ARCHIVED']),
    title: z.string(),
    updatedAt: date,
    publishedAt: date.nullable(),
    rowVersion: z.number().int(),
  })
  .loose();
const adminProblemSchema = z
  .object({
    id,
    slug: z.string(),
    visibility: z.enum(['PRIVATE', 'PUBLIC']),
    status: z.enum(['ACTIVE', 'ARCHIVED']),
    currentPublishedVersionId: id.nullable(),
    versions: z.array(versionSummarySchema),
    createdAt: date,
    updatedAt: date,
    rowVersion: z.number().int(),
  })
  .loose() satisfies z.ZodType<AdminProblem>;
const adminVersionSchema = z
  .object({
    id,
    problemId: id,
    versionNo: z.number().int(),
    status: z.enum(['DRAFT', 'VALIDATING', 'READY_FOR_REVIEW', 'PUBLISHED', 'ARCHIVED']),
    codeMode: z.literal('ACM'),
    title: z.string(),
    statementMarkdown: z.string(),
    inputDescriptionMarkdown: z.string(),
    outputDescriptionMarkdown: z.string(),
    constraintsMarkdown: z.string().nullable(),
    hintMarkdown: z.string().nullable(),
    difficulty: z.enum(['UNRATED', 'EASY', 'MEDIUM', 'HARD']),
    tags: z.array(z.string()),
    samples: z.array(sampleSchema),
    allowedLanguages: z.array(languageSummarySchema.extend({ starterCode: z.string() }).loose()),
    testDataVersion: testDataSchema.nullable(),
    changeSummary: z.string().nullable(),
    createdAt: date,
    updatedAt: date,
    publishedAt: date.nullable(),
    rowVersion: z.number().int(),
  })
  .loose();
const deploymentSchema = z
  .object({
    testDataVersionId: id,
    environmentId: id,
    environmentName: z.string(),
    expectedSha256: z.string(),
    status: z.enum(['PENDING', 'DEPLOYING', 'READY', 'FAILED']),
    deployedSha256: z.string().nullable(),
    deployedAt: date.nullable(),
    errorMessage: z.string().nullable(),
    updatedAt: date,
    rowVersion: z.number().int(),
  })
  .loose() satisfies z.ZodType<TestDataDeployment>;
const calibrationSchema = z
  .object({
    id,
    problemVersionId: id,
    languageId: z.string(),
    environmentId: id,
    status: z.enum(['DRAFT', 'RUNNING', 'VALID', 'FAILED', 'SUPERSEDED']),
    cpuNs: z.number().int().nullable(),
    memoryBytes: z.number().int().nullable(),
    clockNs: z.number().int().nullable(),
    benchmarkSummary: z
      .object({
        sourceSha256: z.string(),
        verdict: z.enum(['AC', 'WA', 'TLE', 'MLE', 'RE', 'CE', 'SE']),
        maxCpuNs: z.number().int().nullable(),
        maxMemoryBytes: z.number().int().nullable(),
        maxClockNs: z.number().int().nullable(),
      })
      .loose()
      .nullable(),
    errorMessage: z.string().nullable(),
    createdAt: date,
    updatedAt: date,
    rowVersion: z.number().int(),
  })
  .loose();
const publishCheckSchema = z
  .object({
    ready: z.boolean(),
    environmentId: id.nullable(),
    checks: z.array(
      z.object({ code: z.string(), passed: z.boolean(), message: z.string() }).loose(),
    ),
  })
  .loose();

export type ProblemSearch = {
  q?: string | undefined;
  difficulty?: 'UNRATED' | 'EASY' | 'MEDIUM' | 'HARD' | undefined;
  tag?: string[] | undefined;
  codeMode?: 'ACM' | 'CORE' | undefined;
  language?: string | undefined;
  sort: 'UPDATED_DESC' | 'UPDATED_ASC' | 'TITLE_ASC';
  cursor?: string | undefined;
  size: number;
};

export const problemKeys = {
  all: ['problems'] as const,
  publicList: (search: ProblemSearch) => [...problemKeys.all, 'public-list', search] as const,
  detail: (slug: string) => [...problemKeys.all, 'detail', slug] as const,
  admin: ['admin-problems'] as const,
  adminList: (q: string, status: ProblemStatus | 'ALL', page: number) =>
    [...problemKeys.admin, 'list', q, status, page] as const,
  adminProblem: (id: string) => [...problemKeys.admin, id] as const,
  version: (problemId: string, versionId: string) =>
    [...problemKeys.admin, problemId, 'version', versionId] as const,
  testData: (problemId: string) => [...problemKeys.admin, problemId, 'test-data'] as const,
  publishCheck: (problemId: string, versionId: string) =>
    [...problemKeys.admin, problemId, versionId, 'publish-check'] as const,
};

function query(search: Record<string, string | number | string[] | undefined>) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(search)) {
    if (Array.isArray(value)) value.forEach((item) => params.append(key, item));
    else if (value !== undefined && value !== '') params.set(key, String(value));
  }
  return params.toString();
}

export async function listProblems(search: ProblemSearch, signal?: AbortSignal) {
  const response = await requestJson(
    `/api/problems?${query(search)}`,
    z.object({ items: z.array(problemSummarySchema) }).loose(),
    { ...(signal === undefined ? {} : { signal }) },
  );
  if (response.meta.pagination?.kind !== 'cursor') throw new Error('题库响应缺少游标分页。');
  return { items: response.data.items, pagination: response.meta.pagination };
}
export function problemListQuery(search: ProblemSearch) {
  return queryOptions({
    queryKey: problemKeys.publicList(search),
    queryFn: ({ signal }) => listProblems(search, signal),
  });
}
export async function getProblem(slug: string, signal?: AbortSignal) {
  return (
    await requestJson(`/api/problems/${slug}`, problemDetailSchema, {
      ...(signal === undefined ? {} : { signal }),
    })
  ).data;
}
export function problemQuery(slug: string) {
  return queryOptions({
    queryKey: problemKeys.detail(slug),
    queryFn: ({ signal }) => getProblem(slug, signal),
  });
}
export async function listAdminProblems(
  q: string,
  status: ProblemStatus | 'ALL',
  page: number,
  signal?: AbortSignal,
) {
  const apiStatus = status === 'ALL' ? undefined : status;
  const response = await requestJson(
    `/api/admin/problems?${query({ q, status: apiStatus, page, size: 20 })}`,
    z.object({ items: z.array(adminProblemSchema) }).loose(),
    { ...(signal === undefined ? {} : { signal }) },
  );
  if (response.meta.pagination?.kind !== 'page') throw new Error('管理列表缺少分页。');
  return { items: response.data.items, pagination: response.meta.pagination };
}
export async function createProblem(request: CreateProblemRequest) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson('/api/admin/problems', adminProblemSchema, {
          method: 'POST',
          body: request,
          csrfToken,
        })
      ).data,
  );
}
export async function getAdminProblem(problemId: string, signal?: AbortSignal) {
  return (
    await requestJson(`/api/admin/problems/${problemId}`, adminProblemSchema, {
      ...(signal === undefined ? {} : { signal }),
    })
  ).data;
}
export async function updateProblem(problemId: string, request: UpdateProblemRequest) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(`/api/admin/problems/${problemId}`, adminProblemSchema, {
          method: 'PATCH',
          body: request,
          csrfToken,
        })
      ).data,
  );
}
export async function getVersion(problemId: string, versionId: string, signal?: AbortSignal) {
  return (
    await requestJson(
      `/api/admin/problems/${problemId}/versions/${versionId}`,
      adminVersionSchema,
      { ...(signal === undefined ? {} : { signal }) },
    )
  ).data as AdminProblemVersion;
}
export async function updateVersion(
  problemId: string,
  versionId: string,
  request: UpdateProblemVersionRequest,
) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(
          `/api/admin/problems/${problemId}/versions/${versionId}`,
          adminVersionSchema,
          { method: 'PATCH', body: request, csrfToken },
        )
      ).data,
  );
}
export async function listTestData(problemId: string, signal?: AbortSignal) {
  return (
    await requestJson(
      `/api/admin/problems/${problemId}/test-data`,
      z.object({ items: z.array(testDataSchema) }).loose(),
      { ...(signal === undefined ? {} : { signal }) },
    )
  ).data.items;
}
export async function uploadTestData(problemId: string, file: File, signal?: AbortSignal) {
  const form = new FormData();
  form.set('file', file, file.name);
  return withCsrf(
    async (csrfToken) =>
      (
        await requestMultipart(`/api/admin/problems/${problemId}/test-data`, form, testDataSchema, {
          csrfToken,
          ...(signal === undefined ? {} : { signal }),
        })
      ).data,
  );
}
export async function bindTestData(
  problemId: string,
  versionId: string,
  request: BindTestDataRequest,
) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(
          `/api/admin/problems/${problemId}/versions/${versionId}/test-data`,
          adminVersionSchema,
          { method: 'PUT', body: request, csrfToken },
        )
      ).data,
  );
}
export async function deployTestData(
  problemId: string,
  versionId: string,
  request: DeployTestDataRequest,
) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(
          `/api/admin/problems/${problemId}/versions/${versionId}/deployment`,
          deploymentSchema,
          { method: 'POST', body: request, csrfToken },
        )
      ).data,
  );
}
export async function calibrate(
  problemId: string,
  versionId: string,
  request: CalibrateProblemRequestWritable,
) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(
          `/api/admin/problems/${problemId}/versions/${versionId}/calibration`,
          calibrationSchema,
          { method: 'POST', body: request, csrfToken },
        )
      ).data,
  );
}
export async function getPublishCheck(problemId: string, versionId: string, signal?: AbortSignal) {
  return (
    await requestJson(
      `/api/admin/problems/${problemId}/versions/${versionId}/publish-check`,
      publishCheckSchema,
      { ...(signal === undefined ? {} : { signal }) },
    )
  ).data as PublishCheck;
}
export async function publish(problemId: string, versionId: string, rowVersion: number) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(
          `/api/admin/problems/${problemId}/versions/${versionId}/publish`,
          adminVersionSchema,
          { method: 'POST', body: { rowVersion }, csrfToken },
        )
      ).data,
  );
}
export async function createRevision(problemId: string, request: CreateProblemRevisionRequest) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(`/api/admin/problems/${problemId}/versions`, adminVersionSchema, {
          method: 'POST',
          body: request,
          csrfToken,
        })
      ).data,
  );
}
export async function archiveProblem(problemId: string, rowVersion: number) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(`/api/admin/problems/${problemId}/archive`, adminProblemSchema, {
          method: 'POST',
          body: { rowVersion },
          csrfToken,
        })
      ).data,
  );
}
export async function deleteVersion(problemId: string, versionId: string, rowVersion: number) {
  return withCsrf((csrfToken) =>
    requestVoid(`/api/admin/problems/${problemId}/versions/${versionId}?rowVersion=${rowVersion}`, {
      method: 'DELETE',
      csrfToken,
    }),
  );
}
