import { queryOptions } from '@tanstack/react-query';
import { z } from 'zod';

import type {
  AdminProblem,
  AdminProblemSummary,
  CalibrateProblemRequestWritable,
  CreateProblemRequest,
  LanguageCalibration,
  ProblemDetail,
  ProblemStatus,
  ProblemSummary,
  PublishCheck,
  TestData,
  UpdateProblemRequest,
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
const difficulty = z.enum(['UNRATED', 'EASY', 'MEDIUM', 'HARD']);
const problemSummarySchema = z
  .object({
    problemId: id,
    slug: z.string(),
    title: z.string(),
    difficulty,
    tags: z.array(z.string()),
    codeMode: z.enum(['ACM', 'CORE']),
    allowedLanguages: z.array(languageSummarySchema),
  })
  .strip() satisfies z.ZodType<ProblemSummary>;
const problemDetailSchema = z
  .object({
    problemId: id,
    slug: z.string(),
    codeMode: z.enum(['ACM', 'CORE']),
    title: z.string(),
    difficulty,
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
    digest: z.string().regex(/^[a-f0-9]{64}$/),
    caseCount: z.number().int(),
    totalBytes: z.number().int(),
    updatedAt: date,
    manifest: z
      .object({
        caseCount: z.number().int(),
        totalBytes: z.number().int(),
        files: z.array(
          z.object({ name: z.string(), sizeBytes: z.number().int(), sha256: z.string() }).loose(),
        ),
      })
      .loose(),
  })
  .loose() satisfies z.ZodType<TestData>;
const adminSummarySchema = z
  .object({
    id,
    slug: z.string(),
    title: z.string(),
    visibility: z.enum(['PRIVATE', 'PUBLIC']),
    status: z.enum(['ACTIVE', 'ARCHIVED']),
    difficulty,
    hasTestData: z.boolean(),
    updatedAt: date,
    publishedAt: date.nullable(),
    rowVersion: z.number().int(),
  })
  .loose() satisfies z.ZodType<AdminProblemSummary>;
/** 题目只有一份内容：管理端模型就是题目本身，没有草稿或版本。 */
const adminProblemSchema = z
  .object({
    id,
    slug: z.string(),
    visibility: z.enum(['PRIVATE', 'PUBLIC']),
    status: z.enum(['ACTIVE', 'ARCHIVED']),
    codeMode: z.literal('ACM'),
    title: z.string(),
    statementMarkdown: z.string(),
    inputDescriptionMarkdown: z.string(),
    outputDescriptionMarkdown: z.string(),
    constraintsMarkdown: z.string().nullable(),
    hintMarkdown: z.string().nullable(),
    difficulty,
    tags: z.array(z.string()),
    samples: z.array(sampleSchema),
    allowedLanguages: z.tuple([
      z
        .object({
          id: z.literal('cpp'),
          displayName: z.literal('C++'),
          starterCode: z.string(),
        })
        .loose(),
    ]),
    testData: testDataSchema.nullable(),
    createdAt: date,
    updatedAt: date,
    publishedAt: date.nullable(),
    rowVersion: z.number().int(),
  })
  .loose() satisfies z.ZodType<AdminProblem>;
const calibrationSchema = z
  .object({
    id,
    problemId: id,
    languageId: z.string(),
    status: z.enum(['DRAFT', 'RUNNING', 'VALID', 'FAILED', 'SUPERSEDED']),
    cpuNs: z.number().int().nullable(),
    memoryBytes: z.number().int().nullable(),
    clockNs: z.number().int().nullable(),
    testDataDigest: z.string(),
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
  .loose() satisfies z.ZodType<LanguageCalibration>;
const publishCheckSchema = z
  .object({
    ready: z.boolean(),
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
  testData: (problemId: string) => [...problemKeys.admin, problemId, 'test-data'] as const,
  publishCheck: (problemId: string) => [...problemKeys.admin, problemId, 'publish-check'] as const,
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
    z.object({ items: z.array(adminSummarySchema) }).loose(),
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
function problemRequest<T>(
  problemId: string,
  path: string,
  schema: z.ZodType<T>,
  method: 'POST' | 'PUT',
  payload: object,
) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(`/api/admin/problems/${problemId}${path}`, schema, {
          method,
          body: payload,
          csrfToken,
        })
      ).data,
  );
}
/** 题目还没有测试数据时后端返回 404 TEST_DATA_NOT_FOUND；工作台用题目里的 testData 判断有没有。 */
export async function getTestData(problemId: string, signal?: AbortSignal) {
  return (
    await requestJson(`/api/admin/problems/${problemId}/test-data`, testDataSchema, {
      ...(signal === undefined ? {} : { signal }),
    })
  ).data;
}
/** 上传即替换：题目只有一份测试数据，数据一换，旧的校准就过期了。 */
export async function replaceTestData(problemId: string, file: File, signal?: AbortSignal) {
  const form = new FormData();
  form.set('file', file, file.name);
  return withCsrf(
    async (csrfToken) =>
      (
        await requestMultipart(`/api/admin/problems/${problemId}/test-data`, form, testDataSchema, {
          method: 'PUT',
          csrfToken,
          ...(signal === undefined ? {} : { signal }),
        })
      ).data,
  );
}
export async function calibrate(problemId: string, request: CalibrateProblemRequestWritable) {
  return problemRequest(problemId, '/calibration', calibrationSchema, 'POST', request);
}
export async function getPublishCheck(problemId: string, signal?: AbortSignal) {
  return (
    await requestJson(`/api/admin/problems/${problemId}/publish-check`, publishCheckSchema, {
      ...(signal === undefined ? {} : { signal }),
    })
  ).data as PublishCheck;
}
export async function publish(problemId: string, rowVersion: number) {
  return problemRequest(problemId, '/publish', adminProblemSchema, 'POST', { rowVersion });
}
export async function unpublish(problemId: string, rowVersion: number) {
  return problemRequest(problemId, '/unpublish', adminProblemSchema, 'POST', { rowVersion });
}
export async function archiveProblem(problemId: string, rowVersion: number) {
  return problemRequest(problemId, '/archive', adminProblemSchema, 'POST', { rowVersion });
}
/** 只能删除从未公开过的题目；公开过的只能归档。 */
export async function deleteProblem(problemId: string, rowVersion: number) {
  return withCsrf((csrfToken) =>
    requestVoid(`/api/admin/problems/${problemId}?rowVersion=${rowVersion}`, {
      method: 'DELETE',
      csrfToken,
    }),
  );
}
