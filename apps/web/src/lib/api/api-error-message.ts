import { ApiError } from './api-client';

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ApiError)) return error instanceof Error ? error.message : fallback;
  const request = error.requestId ? `（请求 ID：${error.requestId}）` : '';
  return `${error.problem?.detail ?? error.message ?? fallback}${request}`;
}
