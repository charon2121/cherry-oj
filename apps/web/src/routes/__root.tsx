import type { QueryClient } from '@tanstack/react-query';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, Link, Outlet } from '@tanstack/react-router';

import { Button } from '@/components/ui/button';
import { Cluster, Container } from '@/components/ui/layout';
import { linkVariants } from '@/components/ui/link';
import { Heading, Text } from '@/components/ui/typography';
import { logout } from '@/features/auth/api/auth-api';
import { authKeys, sessionQueryOptions } from '@/features/auth/api/session-query';
import { authErrorMessage } from '@/features/auth/lib/auth-error-message';
import { ApiError } from '@/lib/api/api-client';

type RouterContext = {
  queryClient: QueryClient;
};

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
  notFoundComponent: NotFoundPage,
});

function RootLayout() {
  return (
    <div className="bg-background text-foreground min-h-svh">
      <header className="border-border bg-sidebar border-b">
        <Container>
          <nav aria-label="主导航" className="flex min-h-12 flex-wrap items-center gap-2 py-1">
            <Link
              to="/"
              className={linkVariants({
                size: 'standalone',
                variant: 'muted',
                className: 'font-display shrink-0 tracking-tight',
              })}
            >
              <span className="text-brand">Cherry</span> OJ
            </Link>
            <SessionNavigation />
          </nav>
        </Container>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}

function SessionNavigation() {
  const queryClient = useQueryClient();
  const session = useQuery(sessionQueryOptions());
  const mutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.setQueryData(authKeys.session(), { authenticated: false });
      window.location.assign('/login');
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 401) {
        queryClient.setQueryData(authKeys.session(), { authenticated: false });
        window.location.assign('/login');
      }
    },
  });

  if (session.isPending) {
    return (
      <Text as="span" role="status" size="xs" tone="muted" className="ml-auto">
        正在检查登录状态…
      </Text>
    );
  }
  if (session.isError) {
    return (
      <Button
        className="ml-auto"
        variant="secondary"
        size="sm"
        onClick={() => void session.refetch()}
      >
        登录状态加载失败，重试
      </Button>
    );
  }
  if (!session.data.authenticated) {
    return (
      <Link
        to="/login"
        search={{ returnTo: '/' }}
        className={linkVariants({ size: 'standalone', className: 'ml-auto text-sm' })}
      >
        登录
      </Link>
    );
  }
  return (
    <Cluster className="ml-auto flex-1 py-1 text-sm" gap={3} justify="end">
      {mutation.isError ? (
        <span role="alert" className="text-danger hidden text-xs md:inline">
          {authErrorMessage(mutation.error)}
        </span>
      ) : null}
      {session.data.user.passwordChangeRequired ? (
        <Link
          to="/account/password"
          className={linkVariants({
            size: 'standalone',
            className: 'text-warning hover:text-warning visited:text-warning text-sm',
          })}
        >
          请先修改密码
        </Link>
      ) : null}
      {session.data.user.role === 'ADMIN' ? (
        <Link
          to="/admin/users"
          search={{ page: 1 }}
          className={linkVariants({ size: 'standalone', className: 'text-sm' })}
        >
          用户管理
        </Link>
      ) : null}
      <Text as="span" size="sm" tone="muted" className="hidden sm:inline">
        {session.data.user.username}
      </Text>
      <Button
        variant="ghost"
        size="sm"
        disabled={mutation.isPending}
        onClick={() => mutation.mutate()}
      >
        {mutation.isPending ? '退出中…' : '退出'}
      </Button>
    </Cluster>
  );
}

function NotFoundPage() {
  return (
    <Container as="section" className="py-16">
      <Text size="sm" tone="muted">
        404
      </Text>
      <Heading level={1} size="2xl" className="mt-2">
        页面不存在
      </Heading>
      <Link to="/" className={linkVariants({ size: 'standalone', className: 'mt-6' })}>
        返回首页
      </Link>
    </Container>
  );
}
