import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { Check, Copy, KeyRound, UserPlus } from 'lucide-react';
import { useEffect, useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Panel } from '@/components/ui/card';
import { FormField } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Cluster, Container, Section, Stack } from '@/components/ui/layout';
import { CodeText, Heading, Text } from '@/components/ui/typography';
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
    <Container>
      <Section>
        <Heading level={1} className="sr-only">
          用户账号
        </Heading>
        <Panel className="p-[var(--ds-space-4)]">
          <form
            className="grid w-full items-end gap-[var(--ds-space-2)] sm:w-auto sm:max-w-xl sm:grid-cols-[minmax(12rem,1fr)_auto]"
            onSubmit={(event) => {
              event.preventDefault();
              if (!create.isPending) create.mutate({ username: newUsername });
            }}
          >
            <FormField label="新用户用户名" required>
              <Input
                id="new-username"
                minLength={3}
                maxLength={64}
                pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,63}"
                value={newUsername}
                onChange={(event) => setNewUsername(event.target.value)}
                placeholder="新用户用户名"
              />
            </FormField>
            <Button type="submit" size="md" loading={create.isPending} loadingLabel="创建中…">
              <UserPlus aria-hidden="true" />
              创建用户
            </Button>
          </form>
        </Panel>

        {mutationError ? (
          <div className="mt-[var(--ds-space-6)]">
            <ErrorNotice message={authErrorMessage(mutationError)} />
          </div>
        ) : null}
        {temporaryPassword !== undefined ? (
          <Panel
            role="dialog"
            aria-labelledby="temporary-password-title"
            className="bg-warning-soft text-warning mt-[var(--ds-space-6)] border-[var(--ds-warning-border)]"
          >
            <Cluster gap={3} justify="between" className="items-center">
              <Stack gap={2}>
                <Heading id="temporary-password-title" level={2} size="lg" className="text-warning">
                  一次性临时密码
                </Heading>
                <Text size="sm" tone="primary" className="text-warning">
                  请立即安全交给用户。关闭后无法再次查看。
                </Text>
                <CodeText className="text-foreground inline-block max-w-full rounded-[var(--ds-radius-xs)] border border-[var(--ds-border-soft)] bg-[var(--ds-surface-recessed)] px-[var(--ds-space-2)] py-[var(--ds-space-1)] wrap-anywhere select-all">
                  {temporaryPassword}
                </CodeText>
              </Stack>
              <Cluster gap={2}>
                <Button
                  variant="secondary"
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
                  variant="secondary"
                  onClick={() => {
                    setTemporaryPassword(undefined);
                    setCopied(false);
                  }}
                >
                  我已保存，关闭
                </Button>
              </Cluster>
            </Cluster>
          </Panel>
        ) : null}

        <Panel className="mt-[var(--ds-space-6)] overflow-x-auto p-0">
          {users.isPending ? (
            <div className="p-[var(--ds-space-6)]">
              <AsyncState
                variant="loading"
                size="inline"
                title="正在加载用户…"
                progressLabel="正在加载用户…"
              >
                {null}
              </AsyncState>
            </div>
          ) : null}
          {users.isError ? (
            <div className="p-[var(--ds-space-6)]">
              <ErrorNotice message={authErrorMessage(users.error)} />
              <Button
                variant="secondary"
                className="mt-[var(--ds-space-3)]"
                onClick={() => void users.refetch()}
              >
                重试
              </Button>
            </div>
          ) : null}
          {users.data ? (
            <table className="w-full min-w-3xl text-left text-[length:var(--ds-text-sm)]">
              <thead className="bg-[var(--ds-surface-translucent)] text-[var(--ds-fg-meta)]">
                <tr>
                  <th className="px-[var(--ds-space-4)] py-[var(--ds-space-3)] font-[var(--ds-weight-body)]">
                    用户名
                  </th>
                  <th className="px-[var(--ds-space-4)] py-[var(--ds-space-3)] font-[var(--ds-weight-body)]">
                    角色
                  </th>
                  <th className="px-[var(--ds-space-4)] py-[var(--ds-space-3)] font-[var(--ds-weight-body)]">
                    状态
                  </th>
                  <th className="px-[var(--ds-space-4)] py-[var(--ds-space-3)] font-[var(--ds-weight-body)]">
                    首次改密
                  </th>
                  <th className="px-[var(--ds-space-4)] py-[var(--ds-space-3)] text-right font-[var(--ds-weight-body)]">
                    操作
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--ds-border-soft)]">
                {users.data.items.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-[var(--ds-space-4)] py-[var(--ds-space-8)]">
                      <AsyncState
                        variant="empty"
                        size="inline"
                        title="暂无用户账号。"
                        className="items-center text-center"
                      >
                        {null}
                      </AsyncState>
                    </td>
                  </tr>
                ) : null}
                {users.data.items.map((user) => (
                  <tr
                    key={user.id}
                    className="transition-colors duration-[var(--ds-motion-fast)] hover:bg-[var(--ds-surface-translucent-hover)] motion-reduce:transition-none"
                  >
                    <td className="px-[var(--ds-space-4)] py-[var(--ds-space-3)] font-[var(--ds-weight-body)]">
                      {user.username}
                    </td>
                    <td className="px-[var(--ds-space-4)] py-[var(--ds-space-3)]">{user.role}</td>
                    <td className="px-[var(--ds-space-4)] py-[var(--ds-space-3)]">
                      <StatusBadge status={user.status} />
                    </td>
                    <td className="px-[var(--ds-space-4)] py-[var(--ds-space-3)]">
                      {user.passwordChangeRequired ? '需要' : '否'}
                    </td>
                    <td className="px-[var(--ds-space-4)] py-[var(--ds-space-3)]">
                      <Cluster gap={2} justify="end">
                        <Button
                          size="sm"
                          variant="secondary"
                          disabled={status.isPending}
                          onClick={() => {
                            const action = user.status === 'ACTIVE' ? '停用' : '恢复';
                            const consequence =
                              user.status === 'ACTIVE'
                                ? '这将结束其全部登录。'
                                : '旧登录不会恢复。';
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
                          variant="secondary"
                          disabled={reset.isPending}
                          onClick={() => {
                            if (
                              window.confirm(
                                `确认重置用户 ${user.username} 的密码并结束其全部登录？`,
                              )
                            )
                              reset.mutate(user);
                          }}
                        >
                          <KeyRound aria-hidden="true" />
                          重置密码
                        </Button>
                      </Cluster>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
        </Panel>
        {users.data ? (
          <nav aria-label="用户分页" className="mt-[var(--ds-space-4)]">
            <Cluster gap={3} justify="between">
              <Text as="span" size="sm" tone="secondary">
                第 {users.data.pagination.page} / {Math.max(1, users.data.pagination.totalPages)}{' '}
                页， 共 {users.data.pagination.totalElements} 个账号
              </Text>
              <Cluster gap={2}>
                <Button
                  variant="secondary"
                  disabled={page <= 1}
                  onClick={() => void navigate({ search: { page: page - 1 } })}
                >
                  上一页
                </Button>
                <Button
                  variant="secondary"
                  disabled={page >= users.data.pagination.totalPages}
                  onClick={() => void navigate({ search: { page: page + 1 } })}
                >
                  下一页
                </Button>
              </Cluster>
            </Cluster>
          </nav>
        ) : null}
      </Section>
    </Container>
  );
}

function StatusBadge({ status }: { status: 'ACTIVE' | 'DISABLED' }) {
  return status === 'ACTIVE' ? (
    <Badge variant="success">正常</Badge>
  ) : (
    <Badge variant="danger">已停用</Badge>
  );
}
