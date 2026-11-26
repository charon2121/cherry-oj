import { Button } from '@/components/ui/button';
import { FormField } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Stack } from '@/components/ui/layout';
import { DetailPageTemplate } from '@/components/ui/page-templates';
import { ErrorNotice } from '@/features/auth/components/error-notice';
import { PasswordField } from '@/features/auth/components/password-field';

type LoginPageViewProps = Readonly<{
  errorMessage: string | undefined;
  password: string;
  pending?: boolean;
  username: string;
  onPasswordChange: (value: string) => void;
  onSubmit: () => void;
  onUsernameChange: (value: string) => void;
}>;

function LoginPageView({
  errorMessage,
  password,
  pending = false,
  username,
  onPasswordChange,
  onSubmit,
  onUsernameChange,
}: LoginPageViewProps) {
  return (
    <DetailPageTemplate title="登录 Cherry OJ" className="max-w-lg">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (!pending) onSubmit();
        }}
      >
        <Stack gap={4} className="[&_input]:min-h-12">
          <FormField label="用户名" required>
            <Input
              id="username"
              name="username"
              autoComplete="username"
              minLength={3}
              maxLength={64}
              pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,63}"
              value={username}
              onChange={(event) => onUsernameChange(event.target.value)}
            />
          </FormField>
          <PasswordField
            id="password"
            label="密码"
            autoComplete="current-password"
            value={password}
            onChange={onPasswordChange}
          />
          <Button
            type="submit"
            size="md"
            className="min-h-12"
            loading={pending}
            loadingLabel="正在登录…"
          >
            登录
          </Button>
          <ErrorNotice message={errorMessage} />
        </Stack>
      </form>
    </DetailPageTemplate>
  );
}

export { LoginPageView, type LoginPageViewProps };
