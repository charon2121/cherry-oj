import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useEffect, useState } from 'react';

import { Button } from '@/components/ui/button';
import { changePassword } from '@/features/auth/api/auth-api';
import { authKeys } from '@/features/auth/api/session-query';
import { ErrorNotice } from '@/features/auth/components/error-notice';
import { PasswordField } from '@/features/auth/components/password-field';
import { authErrorMessage } from '@/features/auth/lib/auth-error-message';
import { requireUser } from '@/features/auth/lib/route-guards';
import { ApiError } from '@/lib/api/api-client';

export const Route = createFileRoute('/account/password')({
  beforeLoad: ({ context }) => requireUser(context.queryClient, '/account/password'),
  component: ChangePasswordPage,
});

function ChangePasswordPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const mismatch = confirmation.length > 0 && confirmation !== newPassword;
  const mutation = useMutation({
    mutationFn: changePassword,
    onSuccess: async () => {
      setCurrentPassword('');
      setNewPassword('');
      setConfirmation('');
      queryClient.setQueryData(authKeys.session(), { authenticated: false });
      await navigate({ to: '/login', search: { returnTo: '/' } });
    },
  });
  useEffect(() => {
    if (!(mutation.error instanceof ApiError) || mutation.error.status !== 401) return;
    queryClient.setQueryData(authKeys.session(), { authenticated: false });
    void navigate({ to: '/login', search: { returnTo: '/account/password' } });
  }, [mutation.error, navigate, queryClient]);

  return (
    <section className="mx-auto max-w-xl px-4 py-12">
      <p className="text-muted-foreground text-sm">账号安全</p>
      <h1 className="mt-1 text-2xl font-semibold">修改密码</h1>
      <p className="text-muted-foreground mt-2 text-sm">
        修改成功后所有设备都会退出，请使用新密码重新登录。
      </p>
      <ErrorNotice message={mutation.isError ? authErrorMessage(mutation.error) : undefined} />
      <form
        className="border-border mt-6 space-y-4 border-t pt-6"
        onSubmit={(event) => {
          event.preventDefault();
          if (!mismatch && !mutation.isPending) mutation.mutate({ currentPassword, newPassword });
        }}
      >
        <PasswordField
          id="current-password"
          label="当前密码"
          value={currentPassword}
          onChange={setCurrentPassword}
          autoComplete="current-password"
        />
        <PasswordField
          id="new-password"
          label="新密码（至少 12 位）"
          value={newPassword}
          onChange={setNewPassword}
          autoComplete="new-password"
          minLength={12}
        />
        <PasswordField
          id="confirm-password"
          label="确认新密码"
          value={confirmation}
          onChange={setConfirmation}
          autoComplete="new-password"
          minLength={12}
          invalid={mismatch}
          errorDescriptionId="password-mismatch"
        />
        {mismatch ? (
          <p id="password-mismatch" className="text-danger text-sm">
            两次输入的新密码不一致。
          </p>
        ) : null}
        <Button type="submit" size="lg" disabled={mutation.isPending || mismatch}>
          {mutation.isPending ? '正在修改…' : '修改密码并退出'}
        </Button>
      </form>
    </section>
  );
}
