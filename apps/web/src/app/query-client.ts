import { QueryClient } from '@tanstack/react-query';

import { ApiError } from '@/lib/api/api-client';

export function shouldRetryQuery(failureCount: number, error: Error) {
  if (failureCount >= 1 || !(error instanceof ApiError)) {
    return false;
  }
  if (error.kind === 'network' || error.kind === 'timeout') {
    return true;
  }
  return error.kind === 'http' && error.status !== undefined && error.status >= 500;
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: shouldRetryQuery,
      refetchOnWindowFocus: true,
    },
    mutations: {
      retry: false,
    },
  },
});
