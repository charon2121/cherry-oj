import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';

import { Button } from '@/components/ui/button';
import { Cluster } from '@/components/ui/layout';
import { linkVariants } from '@/components/ui/link';
import { Text } from '@/components/ui/typography';
import { logout } from '@/features/auth/api/auth-api';
import { authKeys, sessionQueryOptions } from '@/features/auth/api/session-query';
import { authErrorMessage } from '@/features/auth/lib/auth-error-message';
import { ApiError } from '@/lib/api/api-client';
import { cn } from '@/lib/utils';

type SessionActionsProps = Readonly<{
  className?: string;
  showAdminEntry?: boolean;
}>;

function SessionActions({ className, showAdminEntry = false }: SessionActionsProps) {
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
      <Text as="span" role="status" size="xs" tone="muted" className={cn('ml-auto', className)}>
        正在检查登录状态…
      </Text>
    );
  }

  if (session.isError) {
    return (
      <Button
        className={cn('ml-auto', className)}
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
        className={linkVariants({
          size: 'standalone',
          className: cn('ml-auto text-sm', className),
        })}
      >
        登录
      </Link>
    );
  }

  return (
    <Cluster className={cn('ml-auto flex-1 py-1 text-sm', className)} gap={3} justify="end">
      {mutation.isError ? (
        <span role="alert" className="text-danger hidden text-xs lg:inline">
          {authErrorMessage(mutation.error)}
        </span>
      ) : null}
      {session.data.user.passwordChangeRequired ? (
        <Link
          to="/account/password"
          className={linkVariants({
            size: 'standalone',
            className:
              'text-warning hover:text-warning visited:text-warning hidden text-sm sm:inline-flex',
          })}
        >
          请先修改密码
        </Link>
      ) : null}
      {showAdminEntry && session.data.user.role === 'ADMIN' ? (
        <Link
          to="/admin"
          className={linkVariants({
            size: 'standalone',
            className: 'hidden text-sm md:inline-flex',
          })}
        >
          管理中心
        </Link>
      ) : null}
      <Text as="span" size="sm" tone="muted" className="hidden lg:inline">
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

export { SessionActions, type SessionActionsProps };
