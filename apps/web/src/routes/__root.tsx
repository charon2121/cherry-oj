import type { QueryClient } from '@tanstack/react-query';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, Link, Outlet } from '@tanstack/react-router';

import { Button } from '@/components/ui/button';
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
        <nav aria-label="主导航" className="mx-auto flex h-12 max-w-6xl items-center px-4">
          <Link to="/" className="font-semibold tracking-tight">
            <span className="text-brand">Cherry</span> OJ
          </Link>
          <SessionNavigation />
        </nav>
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
      <span role="status" className="text-muted-foreground ml-auto text-xs">
        正在检查登录状态…
      </span>
    );
  }
  if (session.isError) {
    return (
      <Button
        className="ml-auto"
        variant="outline"
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
        className="ml-auto text-sm font-medium underline-offset-4 hover:underline"
      >
        登录
      </Link>
    );
  }
  return (
    <div className="ml-auto flex items-center gap-3 text-sm">
      {mutation.isError ? (
        <span role="alert" className="text-danger hidden text-xs md:inline">
          {authErrorMessage(mutation.error)}
        </span>
      ) : null}
      {session.data.user.passwordChangeRequired ? (
        <Link to="/account/password" className="text-warning font-medium">
          请先修改密码
        </Link>
      ) : null}
      {session.data.user.role === 'ADMIN' ? (
        <Link
          to="/admin/users"
          search={{ page: 1 }}
          className="font-medium underline-offset-4 hover:underline"
        >
          用户管理
        </Link>
      ) : null}
      <span className="text-muted-foreground hidden sm:inline">{session.data.user.username}</span>
      <Button
        variant="ghost"
        size="sm"
        disabled={mutation.isPending}
        onClick={() => mutation.mutate()}
      >
        {mutation.isPending ? '退出中…' : '退出'}
      </Button>
    </div>
  );
}

function NotFoundPage() {
  return (
    <section className="mx-auto max-w-6xl px-4 py-16">
      <p className="text-muted-foreground text-sm">404</p>
      <h1 className="mt-2 text-2xl font-semibold">页面不存在</h1>
      <Link to="/" className="text-primary mt-6 inline-block underline-offset-4 hover:underline">
        返回首页
      </Link>
    </section>
  );
}
