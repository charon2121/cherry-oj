import { queryOptions } from '@tanstack/react-query';

import { getSession } from './auth-api';

export const authKeys = {
  all: ['auth'] as const,
  session: () => [...authKeys.all, 'session'] as const,
};

export function sessionQueryOptions() {
  return queryOptions({
    queryKey: authKeys.session(),
    queryFn: ({ signal }) => getSession(signal),
    staleTime: 15_000,
    gcTime: 5 * 60_000,
  });
}
