import type { QueryClient } from '@tanstack/react-query';
import { redirect } from '@tanstack/react-router';

import { sessionQueryOptions } from '../api/session-query';
import { safeReturnPath } from './safe-return-path';

export async function requireUser(queryClient: QueryClient, returnTo: string) {
  const session = await queryClient.ensureQueryData(sessionQueryOptions());
  if (!session.authenticated) {
    // TanStack Router represents redirects as throwable navigation instructions, not Error.
    // eslint-disable-next-line @typescript-eslint/only-throw-error
    throw redirect({ to: '/login', search: { returnTo: safeReturnPath(returnTo) } });
  }
  if (session.user.passwordChangeRequired && returnTo !== '/account/password') {
    // eslint-disable-next-line @typescript-eslint/only-throw-error
    throw redirect({ to: '/account/password' });
  }
  return session.user;
}

export async function requireAdmin(queryClient: QueryClient, returnTo: string) {
  const user = await requireUser(queryClient, returnTo);
  if (user.role !== 'ADMIN') {
    // eslint-disable-next-line @typescript-eslint/only-throw-error
    throw redirect({ to: '/forbidden' });
  }
  return user;
}
