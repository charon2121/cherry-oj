import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { useState } from 'react';

import { LoginPageView } from '@/app/pages/login-page';
import { login } from '@/features/auth/api/auth-api';
import { authKeys, sessionQueryOptions } from '@/features/auth/api/session-query';
import { authErrorMessage } from '@/features/auth/lib/auth-error-message';
import { safeReturnPath } from '@/features/auth/lib/safe-return-path';

export const Route = createFileRoute('/_site/login')({
  validateSearch: (search: Record<string, unknown>) => ({
    returnTo: safeReturnPath(search.returnTo),
  }),
  beforeLoad: async ({ context }) => {
    const session = await context.queryClient.ensureQueryData(sessionQueryOptions());
    if (session.authenticated) {
      // TanStack Router represents redirects as throwable navigation instructions, not Error.
      // eslint-disable-next-line @typescript-eslint/only-throw-error
      throw redirect({ to: session.user.passwordChangeRequired ? '/account/password' : '/' });
    }
  },
  component: LoginPage,
});

function LoginPage() {
  const { returnTo } = Route.useSearch();
  const queryClient = useQueryClient();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (session) => {
      queryClient.setQueryData(authKeys.session(), session);
      setPassword('');
      window.location.assign(
        session.authenticated && session.user.passwordChangeRequired
          ? '/account/password'
          : returnTo,
      );
    },
  });

  return (
    <LoginPageView
      errorMessage={mutation.isError ? authErrorMessage(mutation.error) : undefined}
      password={password}
      pending={mutation.isPending}
      username={username}
      onPasswordChange={setPassword}
      onSubmit={() => mutation.mutate({ username, password })}
      onUsernameChange={setUsername}
    />
  );
}
