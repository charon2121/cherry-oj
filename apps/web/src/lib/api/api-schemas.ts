import { z } from 'zod';

export const requestIdSchema = z.string().regex(/^req_[A-Za-z0-9_-]{16,64}$/);

const cursorPaginationSchema = z
  .object({
    kind: z.literal('cursor'),
    nextCursor: z.string().min(1).max(2048).nullable(),
    hasMore: z.boolean(),
  })
  .loose();

const pagePaginationSchema = z
  .object({
    kind: z.literal('page'),
    page: z.number().int().min(1),
    size: z.number().int().min(1),
    totalElements: z.number().int().min(0),
    totalPages: z.number().int().min(0),
  })
  .loose();

export const paginationSchema = z.discriminatedUnion('kind', [
  cursorPaginationSchema,
  pagePaginationSchema,
]);

export const apiMetaSchema = z
  .object({
    requestId: requestIdSchema,
    pagination: paginationSchema.optional(),
  })
  .loose();

const fieldViolationSchema = z
  .object({
    path: z.string().min(1).max(512),
    code: z.string().regex(/^[A-Z][A-Z0-9_]{0,63}$/),
    message: z.string().min(1).max(1024),
  })
  .loose();

export const apiProblemSchema = z
  .object({
    type: z.string().min(1),
    title: z.string().min(1).max(256),
    status: z.number().int().min(400).max(599),
    detail: z.string().min(1).max(2048).optional(),
    instance: z.string().min(1).optional(),
    code: z.string().regex(/^[A-Z][A-Z0-9_]{0,63}$/),
    meta: apiMetaSchema,
    violations: z.array(fieldViolationSchema).max(100).optional(),
  })
  .loose();

export function apiSuccessSchema<T>(dataSchema: z.ZodType<T>) {
  return z
    .object({
      data: dataSchema,
      meta: apiMetaSchema,
    })
    .loose();
}
