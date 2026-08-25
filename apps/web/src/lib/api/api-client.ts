import type { z } from 'zod';

import type { ApiMeta, ApiProblem } from '@/generated/api';

import { apiProblemSchema, apiSuccessSchema, requestIdSchema } from './api-schemas';

export type ApiPath = '/api' | `/api/${string}`;
export type ApiErrorKind = 'http' | 'network' | 'timeout' | 'aborted' | 'contract';
export type ApiHttpMethod = 'DELETE' | 'GET' | 'PATCH' | 'POST' | 'PUT';

export type ApiSuccess<T> = {
  data: T;
  meta: ApiMeta;
};

export type ApiJsonResponse<T> = ApiSuccess<T> & {
  status: number;
  location: string | undefined;
};

type ApiErrorOptions = {
  kind: ApiErrorKind;
  status?: number;
  code?: string;
  requestId?: string;
  problem?: ApiProblem;
  retryAfterNs?: bigint;
};

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number | undefined;
  readonly code: string | undefined;
  readonly requestId: string | undefined;
  readonly problem: ApiProblem | undefined;
  readonly retryAfterNs: bigint | undefined;

  constructor(message: string, options: ApiErrorOptions) {
    super(message);
    this.name = 'ApiError';
    this.kind = options.kind;
    this.status = options.status;
    this.code = options.code;
    this.requestId = options.requestId;
    this.problem = options.problem;
    this.retryAfterNs = options.retryAfterNs;
  }
}

export type ApiRequestOptions = {
  method?: ApiHttpMethod;
  body?: object;
  signal?: AbortSignal;
  csrfToken?: string;
  idempotencyKey?: string;
};

function errorOptions(response: Response): Pick<ApiErrorOptions, 'status'> {
  return { status: response.status };
}

function contractError(message: string, response?: Response) {
  return new ApiError(message, {
    kind: 'contract',
    ...(response ? errorOptions(response) : {}),
  });
}

function mediaType(response: Response) {
  return response.headers.get('content-type')?.split(';', 1)[0]?.trim().toLowerCase();
}

function retryAfterNs(response: Response) {
  const value = response.headers.get('retry-after')?.trim();
  if (value === undefined || value.length === 0) {
    return undefined;
  }
  if (/^\d+$/.test(value)) {
    return BigInt(value) * 1_000_000_000n;
  }

  const retryAtMs = Date.parse(value);
  if (Number.isNaN(retryAtMs)) {
    return undefined;
  }
  return BigInt(Math.max(0, retryAtMs - Date.now())) * 1_000_000n;
}

function requireRequestId(response: Response) {
  const result = requestIdSchema.safeParse(response.headers.get('x-request-id'));
  if (!result.success) {
    throw contractError('Gateway 响应缺少有效的 X-Request-Id。', response);
  }
  return result.data;
}

async function readJson(response: Response, expectedMediaType: string) {
  if (mediaType(response) !== expectedMediaType) {
    throw contractError(`Gateway 返回了不受支持的媒体类型，应为 ${expectedMediaType}。`, response);
  }

  try {
    return (await response.json()) as unknown;
  } catch {
    throw contractError('Gateway 返回了无法解析的 JSON。', response);
  }
}

function exceptionName(value: unknown) {
  if (typeof value !== 'object' || value === null || !('name' in value)) {
    return undefined;
  }
  return typeof value.name === 'string' ? value.name : undefined;
}

function failureKind(error: unknown, signal?: AbortSignal): 'aborted' | 'network' | 'timeout' {
  const reason = signal?.reason as unknown;
  if (exceptionName(error) === 'TimeoutError' || exceptionName(reason) === 'TimeoutError') {
    return 'timeout';
  }
  if (signal?.aborted || exceptionName(error) === 'AbortError') {
    return 'aborted';
  }
  return 'network';
}

function failureMessage(kind: 'aborted' | 'network' | 'timeout') {
  switch (kind) {
    case 'aborted':
      return '请求已取消。';
    case 'timeout':
      return '请求超时。';
    case 'network':
      return '无法连接 Gateway。';
  }
}

async function send(path: ApiPath, options: ApiRequestOptions) {
  const headers = new Headers({
    Accept: 'application/json, application/problem+json',
  });
  let body: string | undefined;

  if (options.body !== undefined) {
    if (options.body === null || Array.isArray(options.body)) {
      throw contractError('JSON 请求 body 必须是 object。');
    }
    try {
      body = JSON.stringify(options.body);
    } catch {
      throw contractError('请求 body 无法序列化为 JSON object。');
    }
    if (body === undefined || !body.trimStart().startsWith('{')) {
      throw contractError('请求 body 无法序列化为 JSON object。');
    }
    headers.set('Content-Type', 'application/json');
  }
  if (options.csrfToken !== undefined) {
    headers.set('X-CSRF-Token', options.csrfToken);
  }
  if (options.idempotencyKey !== undefined) {
    headers.set('Idempotency-Key', options.idempotencyKey);
  }

  try {
    return await fetch(path, {
      credentials: 'include',
      headers,
      method: options.method ?? 'GET',
      ...(body === undefined ? {} : { body }),
      ...(options.signal === undefined ? {} : { signal: options.signal }),
    });
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    const kind = failureKind(error, options.signal);
    throw new ApiError(failureMessage(kind), { kind });
  }
}

async function throwProblem(response: Response): Promise<never> {
  const requestId = requireRequestId(response);
  const body = await readJson(response, 'application/problem+json');
  const result = apiProblemSchema.safeParse(body);

  if (!result.success) {
    throw contractError('Gateway 错误响应不符合 ApiProblem 契约。', response);
  }
  if (result.data.status !== response.status) {
    throw contractError('Gateway 错误响应的 HTTP status 与 body.status 不一致。', response);
  }
  if (result.data.meta.requestId !== requestId) {
    throw contractError('Gateway 错误响应的 header 与 body request ID 不一致。', response);
  }

  const problem = result.data as ApiProblem;
  const retryDelayNs = retryAfterNs(response);
  throw new ApiError(problem.detail ?? problem.title, {
    kind: 'http',
    status: response.status,
    code: problem.code,
    requestId,
    problem,
    ...(retryDelayNs === undefined ? {} : { retryAfterNs: retryDelayNs }),
  });
}

export async function requestJson<T>(
  path: ApiPath,
  dataSchema: z.ZodType<T>,
  options: ApiRequestOptions = {},
): Promise<ApiJsonResponse<T>> {
  const response = await send(path, options);
  if (!response.ok) {
    return throwProblem(response);
  }
  if (response.status === 204) {
    throw contractError('JSON 请求收到了无响应体的 204。', response);
  }

  const requestId = requireRequestId(response);
  const body = await readJson(response, 'application/json');
  const result = apiSuccessSchema(dataSchema).safeParse(body);
  if (!result.success) {
    throw contractError('Gateway 成功响应不符合 ApiSuccess 契约。', response);
  }
  if (result.data.meta.requestId !== requestId) {
    throw contractError('Gateway 成功响应的 header 与 body request ID 不一致。', response);
  }

  const location = response.headers.get('location') ?? undefined;
  if ((response.status === 201 || response.status === 202) && location === undefined) {
    throw contractError('201/202 响应必须提供 Location。', response);
  }

  return {
    ...(result.data as ApiSuccess<T>),
    status: response.status,
    location,
  };
}

export async function requestVoid(
  path: ApiPath,
  options: ApiRequestOptions = {},
): Promise<ApiMeta> {
  const response = await send(path, options);
  if (!response.ok) {
    return throwProblem(response);
  }
  if (response.status !== 204) {
    throw contractError('无响应体请求必须返回 204。', response);
  }

  return { requestId: requireRequestId(response) };
}
