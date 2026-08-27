import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import { Check, Copy, KeyRound, UserPlus } from 'lucide-react';
import { useEffect, useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  adminUserKeys,
  createUser,
  listUsers,
  resetUserPassword,
  updateUserStatus,
} from '@/features/admin-users/api/admin-users-api';
import { authKeys } from '@/features/auth/api/session-query';
import { ErrorNotice } from '@/features/auth/components/error-notice';
import { authErrorMessage } from '@/features/auth/lib/auth-error-message';
import { requireAdmin } from '@/features/auth/lib/route-guards';
import type { UserAccountData } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';

const PAGE_SIZE = 20;

export const Route = createFileRoute('/admin/users')({
  validateSearch: (search: Record<string, unknown>) => ({
    page:
      typeof search.page === 'number' && Number.isInteger(search.page) && search.page > 0
        ? search.page
        : 1,
  }),
  beforeLoad: ({ context, location }) => requireAdmin(context.queryClient, location.href),
  component: AdminUsersPage,
});

function AdminUsersPage() {
  const { page } = Route.useSearch();
  const navigate = Route.useNavigate();
  const queryClient = useQueryClient();
  const [newUsername, setNewUsername] = useState('');
  const [temporaryPassword, setTemporaryPassword] = useState<string>();
  const [copied, setCopied] = useState(false);
  const users = useQuery({
    queryKey: adminUserKeys.list(page, PAGE_SIZE),
    queryFn: ({ signal }) => listUsers(page, PAGE_SIZE, signal),
    placeholderData: keepPreviousData,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: adminUserKeys.all });
  const create = useMutation({
    mutationFn: createUser,
    onSuccess: async (result) => {
      setNewUsername('');
      setTemporaryPassword(result.temporaryPassword);
      setCopied(false);
      await refresh();
    },
  });
  const status = useMutation({
    mutationFn: ({
      user,
      nextStatus,
    }: {
      user: UserAccountData;
      nextStatus: 'ACTIVE' | 'DISABLED';
    }) => updateUserStatus(user.id, { status: nextStatus, rowVersion: user.rowVersion }),
    onSuccess: refresh,
  });
  const reset = useMutation({
    mutationFn: (user: UserAccountData) =>
      resetUserPassword(user.id, { rowVersion: user.rowVersion }),
    onSuccess: async (result) => {
      setTemporaryPassword(result.temporaryPassword);
      setCopied(false);
      await refresh();
    },
  });
  const mutationError = create.error ?? status.error ?? reset.error;
  const unauthenticated = [users.error, mutationError].some(
    (error) => error instanceof ApiError && error.status === 401,
  );
  useEffect(() => {
    if (!unauthenticated) return;
    queryClient.setQueryData(authKeys.session(), { authenticated: false });
    void navigate({
      to: '/login',
      search: { returnTo: `/admin/users?page=${page}` },
    });
  }, [navigate, page, queryClient, unauthenticated]);

  return (
    <section className="mx-auto max-w-6xl px-4 py-10">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-muted-foreground text-sm">管理中心</p>
          <h1 className="mt-1 text-2xl font-semibold">用户账号</h1>
          <p className="text-muted-foreground mt-2 text-sm">
            创建、停用、恢复账号或签发一次性临时密码。
          </p>
        </div>
        <form
          className="flex gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            if (!create.isPending) create.mutate({ username: newUsername });
          }}
        >
          <label className="sr-only" htmlFor="new-username">
            新用户用户名
          </label>
          <input
            id="new-username"
            required
            minLength={3}
            maxLength={64}
            pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,63}"
            value={newUsername}
            onChange={(event) => setNewUsername(event.target.value)}
            placeholder="新用户用户名"
            className="border-input bg-background focus-visible:ring-ring h-9 rounded-md border px-3 text-sm outline-none focus-visible:ring-2"
          />
          <Button type="submit" size="lg" disabled={create.isPending}>
            <UserPlus aria-hidden="true" />
            {create.isPending ? '创建中…' : '创建用户'}
          </Button>
        </form>
      </div>

      <ErrorNotice message={mutationError ? authErrorMessage(mutationError) : undefined} />
      {temporaryPassword !== undefined ? (
        <div
          role="dialog"
          aria-labelledby="temporary-password-title"
          className="border-warning bg-warning-soft mt-6 rounded-lg border p-4"
        >
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 id="temporary-password-title" className="font-semibold">
                一次性临时密码
              </h2>
              <p className="mt-1 text-sm">请立即安全交给用户。关闭后无法再次查看。</p>
              <code className="bg-background mt-3 rounded px-2 py-1 text-sm select-all">
                {temporaryPassword}
              </code>
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                onClick={() => {
                  void navigator.clipboard
                    .writeText(temporaryPassword)
                    .then(() => setCopied(true))
                    .catch(() => setCopied(false));
                }}
              >
                {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
                {copied ? '已复制' : '复制'}
              </Button>
              <Button
                variant="outline"
                onClick={() => {
                  setTemporaryPassword(undefined);
                  setCopied(false);
                }}
              >
                我已保存，关闭
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      <div className="border-border mt-6 overflow-x-auto rounded-lg border">
        {users.isPending ? (
          <p role="status" className="p-6 text-sm">
            正在加载用户…
          </p>
        ) : null}
        {users.isError ? (
          <div className="p-6">
            <ErrorNotice message={authErrorMessage(users.error)} />
            <Button variant="outline" className="mt-3" onClick={() => void users.refetch()}>
              重试
            </Button>
          </div>
        ) : null}
        {users.data ? (
          <table className="w-full min-w-3xl text-left text-sm">
            <thead className="bg-surface-subtle text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">用户名</th>
                <th className="px-4 py-3 font-medium">角色</th>
                <th className="px-4 py-3 font-medium">状态</th>
                <th className="px-4 py-3 font-medium">首次改密</th>
                <th className="px-4 py-3 text-right font-medium">操作</th>
              </tr>
            </thead>
            <tbody className="divide-border divide-y">
              {users.data.items.length === 0 ? (
                <tr>
                  <td colSpan={5} className="text-muted-foreground px-4 py-8 text-center">
                    暂无用户账号。
                  </td>
                </tr>
              ) : null}
              {users.data.items.map((user) => (
                <tr key={user.id}>
                  <td className="px-4 py-3 font-medium">{user.username}</td>
                  <td className="px-4 py-3">{user.role}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={user.status} />
                  </td>
                  <td className="px-4 py-3">{user.passwordChangeRequired ? '需要' : '否'}</td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={status.isPending}
                        onClick={() => {
                          const action = user.status === 'ACTIVE' ? '停用' : '恢复';
                          const consequence =
                            user.status === 'ACTIVE' ? '这将结束其全部登录。' : '旧登录不会恢复。';
                          if (
                            window.confirm(`确认${action}用户 ${user.username}？${consequence}`)
                          ) {
                            status.mutate({
                              user,
                              nextStatus: user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
                            });
                          }
                        }}
                      >
                        {user.status === 'ACTIVE' ? '停用' : '恢复'}
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={reset.isPending}
                        onClick={() => {
                          if (
                            window.confirm(`确认重置用户 ${user.username} 的密码并结束其全部登录？`)
                          )
                            reset.mutate(user);
                        }}
                      >
                        <KeyRound aria-hidden="true" />
                        重置密码
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}
      </div>
      {users.data ? (
        <nav aria-label="用户分页" className="mt-4 flex items-center justify-between text-sm">
          <span>
            第 {users.data.pagination.page} / {Math.max(1, users.data.pagination.totalPages)} 页，
            共 {users.data.pagination.totalElements} 个账号
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              disabled={page <= 1}
              onClick={() => void navigate({ search: { page: page - 1 } })}
            >
              上一页
            </Button>
            <Button
              variant="outline"
              disabled={page >= users.data.pagination.totalPages}
              onClick={() => void navigate({ search: { page: page + 1 } })}
            >
              下一页
            </Button>
          </div>
        </nav>
      ) : null}
      <Link to="/" className="mt-8 inline-block text-sm underline underline-offset-4">
        返回首页
      </Link>
    </section>
  );
}

function StatusBadge({ status }: { status: 'ACTIVE' | 'DISABLED' }) {
  return status === 'ACTIVE' ? (
    <span className="bg-success-soft text-success rounded-full px-2 py-1 text-xs font-medium">
      ● 正常
    </span>
  ) : (
    <span className="bg-danger-soft text-danger rounded-full px-2 py-1 text-xs font-medium">
      ○ 已停用
    </span>
  );
}
