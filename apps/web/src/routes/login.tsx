import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { login } from '@/features/auth/api/auth-api';
import { authKeys, sessionQueryOptions } from '@/features/auth/api/session-query';
import { ErrorNotice } from '@/features/auth/components/error-notice';
import { PasswordField } from '@/features/auth/components/password-field';
import { authErrorMessage } from '@/features/auth/lib/auth-error-message';
import { safeReturnPath } from '@/features/auth/lib/safe-return-path';

export const Route = createFileRoute('/login')({
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
    <section className="mx-auto grid min-h-[calc(100svh-3rem)] max-w-6xl place-items-center px-4 py-12">
      <div className="border-border bg-surface-raised w-full max-w-sm rounded-lg border p-6">
        <p className="text-muted-foreground text-sm">安全登录</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight">登录 Cherry OJ</h1>
        <p className="text-muted-foreground mt-2 text-sm">
          账号由管理员开通，密码不会保存在浏览器中。
        </p>
        <ErrorNotice message={mutation.isError ? authErrorMessage(mutation.error) : undefined} />
        <form
          className="mt-6 space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            if (!mutation.isPending) mutation.mutate({ username, password });
          }}
        >
          <label className="block text-sm font-medium" htmlFor="username">
            用户名
          </label>
          <input
            id="username"
            name="username"
            autoComplete="username"
            required
            minLength={3}
            maxLength={64}
            pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,63}"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            className="border-input bg-background focus-visible:ring-ring mt-1 h-10 w-full rounded-md border px-3 outline-none focus-visible:ring-2"
          />
          <PasswordField
            id="password"
            label="密码"
            autoComplete="current-password"
            value={password}
            onChange={setPassword}
          />
          <Button type="submit" size="lg" className="w-full" disabled={mutation.isPending}>
            {mutation.isPending ? '正在登录…' : '登录'}
          </Button>
        </form>
      </div>
    </section>
  );
}
