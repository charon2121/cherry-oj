import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { ArrowLeft, ChevronDown, KeyRound, LayoutDashboard, LogOut, UserRound } from 'lucide-react';
import type { ReactElement } from 'react';

import { Button, buttonVariants } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuLinkItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Text } from '@/components/ui/typography';
import { logout } from '@/features/auth/api/auth-api';
import { authKeys, sessionQueryOptions } from '@/features/auth/api/session-query';
import { authErrorMessage } from '@/features/auth/lib/auth-error-message';
import { ApiError } from '@/lib/api/api-client';
import { cn } from '@/lib/utils';

type AccountMenuProps = Readonly<{
  className?: string;
  showAdminEntry?: boolean;
  showSiteEntry?: boolean;
}>;

type AccountMenuViewProps = Readonly<{
  adminLink: ReactElement;
  className?: string;
  defaultOpen?: boolean;
  logoutError: string | null;
  logoutState: 'error' | 'idle' | 'pending';
  onLogout: () => void;
  passwordChangeRequired: boolean;
  passwordLink: ReactElement;
  role: 'ADMIN' | 'USER';
  showAdminEntry?: boolean;
  showSiteEntry?: boolean;
  siteLink: ReactElement;
  username: string;
}>;

function AccountMenuView({
  adminLink,
  className,
  defaultOpen = false,
  logoutError,
  logoutState,
  onLogout,
  passwordChangeRequired,
  passwordLink,
  role,
  showAdminEntry = false,
  showSiteEntry = false,
  siteLink,
  username,
}: AccountMenuViewProps) {
  const hasLogoutError = logoutState === 'error';
  const triggerStatus =
    logoutState === 'pending'
      ? '退出中…'
      : hasLogoutError
        ? '退出失败'
        : passwordChangeRequired
          ? '需改密'
          : null;
  const triggerLabel = [
    `账号菜单，${username}`,
    passwordChangeRequired ? '需要修改密码' : null,
    hasLogoutError ? '上次退出失败' : null,
  ]
    .filter(Boolean)
    .join('，');

  return (
    <div className={cn('ml-auto min-w-0 shrink-0', className)}>
      <DropdownMenu defaultOpen={defaultOpen}>
        <DropdownMenuTrigger
          render={
            <Button variant="ghost" size="sm" className="max-w-48 px-2" aria-label={triggerLabel} />
          }
        >
          <UserRound aria-hidden="true" />
          {triggerStatus ? (
            <span className={cn(hasLogoutError && 'text-destructive')}>{triggerStatus}</span>
          ) : (
            <span className="hidden max-w-28 truncate sm:inline" title={username}>
              {username}
            </span>
          )}
          <ChevronDown aria-hidden="true" className="hidden sm:block" />
        </DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuGroup>
            <DropdownMenuLabel>
              <span>登录账号</span>
              <span
                className="text-foreground max-w-48 truncate text-sm font-[var(--ds-weight-heading)]"
                title={username}
              >
                {username}
              </span>
            </DropdownMenuLabel>
          </DropdownMenuGroup>
          {logoutError ? (
            <div role="alert" className="text-destructive px-2 py-1.5 text-xs">
              {logoutError}
            </div>
          ) : null}
          <DropdownMenuGroup>
            {showSiteEntry ? (
              <DropdownMenuLinkItem render={siteLink}>
                <ArrowLeft aria-hidden="true" />
                返回用户端
              </DropdownMenuLinkItem>
            ) : null}
            <DropdownMenuLinkItem render={passwordLink}>
              <KeyRound aria-hidden="true" />
              {passwordChangeRequired ? '请先修改密码' : '修改密码'}
            </DropdownMenuLinkItem>
            {showAdminEntry && role === 'ADMIN' ? (
              <DropdownMenuLinkItem render={adminLink}>
                <LayoutDashboard aria-hidden="true" />
                管理中心
              </DropdownMenuLinkItem>
            ) : null}
          </DropdownMenuGroup>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            variant="danger"
            disabled={logoutState === 'pending'}
            onClick={onLogout}
          >
            <LogOut aria-hidden="true" />
            {logoutState === 'pending' ? '退出中…' : hasLogoutError ? '重试退出' : '退出登录'}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}

function AccountMenu({
  className,
  showAdminEntry = false,
  showSiteEntry = false,
}: AccountMenuProps) {
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
      <Text
        as="span"
        role="status"
        size="xs"
        tone="muted"
        className={cn('ml-auto shrink-0', className)}
      >
        正在检查登录状态…
      </Text>
    );
  }

  if (session.isError) {
    return (
      <Button
        className={cn('ml-auto shrink-0', className)}
        variant="secondary"
        size="sm"
        aria-label="登录状态加载失败，重试"
        onClick={() => void session.refetch()}
      >
        重试登录状态
      </Button>
    );
  }

  if (!session.data.authenticated) {
    return (
      <Link
        to="/login"
        search={{ returnTo: '/' }}
        className={buttonVariants({
          variant: 'ghost',
          size: 'sm',
          className: cn('ml-auto rounded-md px-3 no-underline', className),
        })}
      >
        登录
      </Link>
    );
  }

  const { user } = session.data;

  return (
    <AccountMenuView
      {...(className === undefined ? {} : { className })}
      adminLink={<Link to="/admin" />}
      logoutError={mutation.isError ? authErrorMessage(mutation.error) : null}
      logoutState={mutation.isPending ? 'pending' : mutation.isError ? 'error' : 'idle'}
      onLogout={() => mutation.mutate()}
      passwordChangeRequired={user.passwordChangeRequired}
      passwordLink={<Link to="/account/password" />}
      role={user.role}
      showAdminEntry={showAdminEntry}
      showSiteEntry={showSiteEntry}
      siteLink={<Link to="/" />}
      username={user.username}
    />
  );
}

export { AccountMenu, type AccountMenuProps, AccountMenuView, type AccountMenuViewProps };
