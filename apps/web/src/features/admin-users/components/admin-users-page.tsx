import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { Check, Copy, Ellipsis, KeyRound, Plus, UserCheck, UserX } from 'lucide-react';
import { type FormEvent, useEffect, useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { FormField } from '@/components/ui/field';
import { IconButton } from '@/components/ui/icon-button';
import { Input } from '@/components/ui/input';
import { Cluster } from '@/components/ui/layout';
import { CodeText, Heading, Text } from '@/components/ui/typography';
import { authKeys } from '@/features/auth/api/session-query';
import { ErrorNotice } from '@/features/auth/components/error-notice';
import { authErrorMessage } from '@/features/auth/lib/auth-error-message';
import type { UserAccountData } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';

import {
  adminUserKeys,
  createUser,
  listUsers,
  resetUserPassword,
  updateUserStatus,
} from '../api/admin-users-api';

const PAGE_SIZE = 20;

// 构图取自 linear.app 的 Settings › Members（docs/design-system/measurements.md）：页标题、一行
// 工具条（主操作在右）、灰色列头、按状态分组的 32px 分组带、50px 的行（头像 + 名字两行）、其余列 12px 灰。
// 行操作收进行尾的"…"菜单，不在每行平铺按钮。
const columns =
  'grid grid-cols-[minmax(0,1fr)_7rem_7rem_2rem] items-center gap-4 sm:grid-cols-[minmax(0,1fr)_8rem_8rem_8rem_2rem]';

function roleLabel(role: UserAccountData['role']) {
  return role === 'ADMIN' ? '管理员' : '用户';
}

function UserAvatar({ username }: { username: string }) {
  return (
    <span
      aria-hidden="true"
      className="bg-surface-hover text-fg-2 text-cap flex size-6 shrink-0 items-center justify-center rounded-full uppercase"
    >
      {username.slice(0, 1)}
    </span>
  );
}

function TemporaryPasswordDialog({
  onClose,
  password,
}: {
  onClose: () => void;
  password: string | undefined;
}) {
  const [copied, setCopied] = useState(false);
  return (
    <Dialog
      open={password !== undefined}
      onOpenChange={(open) => {
        if (!open) {
          setCopied(false);
          onClose();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>一次性临时密码</DialogTitle>
          <DialogDescription>请立即安全交给用户。关闭后无法再次查看。</DialogDescription>
        </DialogHeader>
        <CodeText className="text-foreground bg-surface block rounded-lg px-3 py-2 wrap-anywhere select-all">
          {password}
        </CodeText>
        <DialogFooter>
          <Button
            variant="secondary"
            onClick={() => {
              if (password === undefined) return;
              void navigator.clipboard
                .writeText(password)
                .then(() => setCopied(true))
                .catch(() => setCopied(false));
            }}
          >
            {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
            {copied ? '已复制' : '复制'}
          </Button>
          <DialogClose render={<Button />}>我已保存，关闭</DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function CreateUserDialog({
  creating,
  onCreate,
}: {
  creating: boolean;
  onCreate: (username: string) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [username, setUsername] = useState('');
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (creating) return;
    await onCreate(username);
    setUsername('');
    setOpen(false);
  };
  return (
    <Dialog open={open} onOpenChange={(next) => !creating && setOpen(next)}>
      <DialogTrigger render={<Button />}>
        <Plus aria-hidden="true" />
        新建账号
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新建账号</DialogTitle>
          <DialogDescription>系统会生成一次性临时密码，用户首次登录时必须修改。</DialogDescription>
        </DialogHeader>
        <form className="grid gap-5" onSubmit={(event) => void submit(event)}>
          <FormField label="用户名" required description="3–64 位字母、数字、点、下划线或连字符。">
            <Input
              minLength={3}
              maxLength={64}
              pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,63}"
              value={username}
              autoComplete="off"
              onChange={(event) => setUsername(event.target.value)}
            />
          </FormField>
          <DialogFooter>
            <DialogClose render={<Button variant="secondary" />} disabled={creating}>
              取消
            </DialogClose>
            <Button type="submit" loading={creating} loadingLabel="正在创建…">
              创建账号
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function UserRowActions({
  busy,
  onReset,
  onToggleStatus,
  user,
}: {
  busy: boolean;
  onReset: () => void;
  onToggleStatus: () => void;
  user: UserAccountData;
}) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <IconButton label={`${user.username} 的更多操作`} size="md" disabled={busy}>
            <Ellipsis aria-hidden="true" />
          </IconButton>
        }
      />
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={onReset}>
          <KeyRound aria-hidden="true" />
          重置密码
        </DropdownMenuItem>
        <DropdownMenuItem
          variant={user.status === 'ACTIVE' ? 'danger' : 'default'}
          onClick={onToggleStatus}
        >
          {user.status === 'ACTIVE' ? (
            <UserX aria-hidden="true" />
          ) : (
            <UserCheck aria-hidden="true" />
          )}
          {user.status === 'ACTIVE' ? '停用' : '恢复'}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export function AdminUsersPage({ page }: { page: number }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [temporaryPassword, setTemporaryPassword] = useState<string>();
  const users = useQuery({
    queryKey: adminUserKeys.list(page, PAGE_SIZE),
    queryFn: ({ signal }) => listUsers(page, PAGE_SIZE, signal),
    placeholderData: keepPreviousData,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: adminUserKeys.all });
  const create = useMutation({
    mutationFn: createUser,
    onSuccess: async (result) => {
      setTemporaryPassword(result.temporaryPassword);
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
    void navigate({ to: '/login', search: { returnTo: `/admin/users?page=${page}` } });
  }, [navigate, page, queryClient, unauthenticated]);

  const items = users.data?.items ?? [];
  const groups = [
    { key: 'active', label: '正常', rows: items.filter((user) => user.status === 'ACTIVE') },
    { key: 'disabled', label: '已停用', rows: items.filter((user) => user.status === 'DISABLED') },
  ].filter((group) => group.rows.length > 0);

  const toggleStatus = (user: UserAccountData) => {
    const action = user.status === 'ACTIVE' ? '停用' : '恢复';
    const consequence = user.status === 'ACTIVE' ? '这将结束其全部登录。' : '旧登录不会恢复。';
    if (window.confirm(`确认${action}用户 ${user.username}？${consequence}`)) {
      status.mutate({ user, nextStatus: user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' });
    }
  };
  const resetPassword = (user: UserAccountData) => {
    if (window.confirm(`确认重置用户 ${user.username} 的密码并结束其全部登录？`))
      reset.mutate(user);
  };

  return (
    <div className="mx-auto w-full max-w-[72rem] px-4 pt-6 pb-12 sm:px-14">
      <Heading level={1} size="xl" className="font-body text-2xl">
        账号
      </Heading>
      <Cluster gap={2} justify="between" className="mt-4">
        <Text size="xs" tone="meta">
          {users.data ? `共 ${users.data.pagination.totalElements} 个账号` : null}
        </Text>
        <CreateUserDialog
          creating={create.isPending}
          onCreate={async (username) => {
            await create.mutateAsync({ username });
          }}
        />
      </Cluster>

      {mutationError ? (
        <div className="mt-4">
          <ErrorNotice message={authErrorMessage(mutationError)} />
        </div>
      ) : null}

      <div className="mt-6">
        {users.isPending ? (
          <AsyncState
            variant="loading"
            size="inline"
            title="正在加载账号…"
            progressLabel="正在加载账号…"
          >
            {null}
          </AsyncState>
        ) : users.isError ? (
          <AsyncState
            variant="error"
            size="inline"
            title="账号列表加载失败"
            action={
              <Button variant="secondary" onClick={() => void users.refetch()}>
                重试
              </Button>
            }
          >
            {authErrorMessage(users.error)}
          </AsyncState>
        ) : items.length === 0 ? (
          <AsyncState variant="empty" size="inline" title="还没有账号">
            用右上角的「新建账号」开始。
          </AsyncState>
        ) : (
          <div role="table" aria-label="账号" className="min-w-0">
            <div role="row" className={`${columns} text-fg-meta text-cap px-2 pb-2`}>
              <span role="columnheader">用户名</span>
              <span role="columnheader">角色</span>
              <span role="columnheader" className="max-sm:hidden">
                登录
              </span>
              <span role="columnheader">状态</span>
              <span role="columnheader" className="sr-only">
                操作
              </span>
            </div>
            {groups.map((group) => (
              <div role="rowgroup" key={group.key}>
                <div
                  role="row"
                  className="bg-surface text-fg-meta text-cap font-body mb-half flex min-h-8 items-center gap-2 rounded-lg px-2"
                >
                  <span role="cell">
                    {group.label} <span className="font-regular">{group.rows.length}</span>
                  </span>
                </div>
                {group.rows.map((user) => (
                  <div
                    role="row"
                    key={user.id}
                    className={`${columns} hover:bg-surface-translucent-hover duration-fast min-h-[50px] rounded-lg px-2 transition-colors motion-reduce:transition-none`}
                  >
                    <span role="cell" className="gap-2x flex min-w-0 items-center">
                      <UserAvatar username={user.username} />
                      <span className="min-w-0">
                        <span className="text-fg-2 font-body block truncate text-sm">
                          {user.username}
                        </span>
                      </span>
                    </span>
                    <span role="cell" className="text-fg-meta text-cap">
                      {roleLabel(user.role)}
                    </span>
                    <span role="cell" className="text-fg-meta text-cap max-sm:hidden">
                      {user.passwordChangeRequired ? '待首次改密' : '已改密'}
                    </span>
                    <span
                      role="cell"
                      className={
                        user.status === 'ACTIVE' ? 'text-fg-meta text-cap' : 'text-danger text-cap'
                      }
                    >
                      {user.status === 'ACTIVE' ? '正常' : '已停用'}
                    </span>
                    <span role="cell" className="flex justify-end">
                      <UserRowActions
                        user={user}
                        busy={status.isPending || reset.isPending}
                        onReset={() => resetPassword(user)}
                        onToggleStatus={() => toggleStatus(user)}
                      />
                    </span>
                  </div>
                ))}
              </div>
            ))}
          </div>
        )}
      </div>

      {users.data && users.data.pagination.totalPages > 1 ? (
        <nav aria-label="账号分页" className="mt-4">
          <Cluster gap={3} justify="between">
            <Text as="span" size="sm" tone="meta">
              第 {users.data.pagination.page} / {users.data.pagination.totalPages} 页
            </Text>
            <Cluster gap={2}>
              <Button
                variant="secondary"
                size="sm"
                disabled={page <= 1}
                onClick={() => void navigate({ to: '/admin/users', search: { page: page - 1 } })}
              >
                上一页
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={page >= users.data.pagination.totalPages}
                onClick={() => void navigate({ to: '/admin/users', search: { page: page + 1 } })}
              >
                下一页
              </Button>
            </Cluster>
          </Cluster>
        </nav>
      ) : null}

      <TemporaryPasswordDialog
        password={temporaryPassword}
        onClose={() => setTemporaryPassword(undefined)}
      />
    </div>
  );
}
