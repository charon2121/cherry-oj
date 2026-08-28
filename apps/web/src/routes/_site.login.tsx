import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Field, Input } from '@/components/ui/field';
import { Container, Section, Stack } from '@/components/ui/layout';
import { Heading, Text } from '@/components/ui/typography';
import { login } from '@/features/auth/api/auth-api';
import { authKeys, sessionQueryOptions } from '@/features/auth/api/session-query';
import { ErrorNotice } from '@/features/auth/components/error-notice';
import { PasswordField } from '@/features/auth/components/password-field';
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
    <Container className="h-full">
      <Section className="grid min-h-full place-items-center py-12">
        <Card variant="raised" className="w-full max-w-sm p-6">
          <Stack gap={2}>
            <Text size="sm" tone="muted">
              安全登录
            </Text>
            <Heading level={1} size="2xl">
              登录 Cherry OJ
            </Heading>
            <Text size="sm" tone="muted">
              账号由管理员开通，密码不会保存在浏览器中。
            </Text>
          </Stack>
          <ErrorNotice message={mutation.isError ? authErrorMessage(mutation.error) : undefined} />
          <form
            className="mt-6"
            onSubmit={(event) => {
              event.preventDefault();
              if (!mutation.isPending) mutation.mutate({ username, password });
            }}
          >
            <Stack gap={4}>
              <Field label="用户名" required>
                <Input
                  id="username"
                  name="username"
                  autoComplete="username"
                  minLength={3}
                  maxLength={64}
                  pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,63}"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                />
              </Field>
              <PasswordField
                id="password"
                label="密码"
                autoComplete="current-password"
                value={password}
                onChange={setPassword}
              />
              <Button
                type="submit"
                size="md"
                className="w-full"
                loading={mutation.isPending}
                loadingLabel="正在登录…"
              >
                登录
              </Button>
            </Stack>
          </form>
        </Card>
      </Section>
    </Container>
  );
}
