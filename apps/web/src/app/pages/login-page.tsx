import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { FormField } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Container, Section, Stack } from '@/components/ui/layout';
import { Heading, Text } from '@/components/ui/typography';
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
    <Container className="max-w-[440px]">
      <Section className="py-6 sm:py-10">
        <Card padding="lg" className="p-6 sm:p-7">
          <Stack gap={6}>
            <div className="space-y-1.5">
              <Heading level={1} size="xl">
                登录 Cherry OJ
              </Heading>
              <Text size="sm" tone="muted">
                输入平台账号与密码以继续访问工作台。
              </Text>
            </div>
            <form
              onSubmit={(event) => {
                event.preventDefault();
                if (!pending) onSubmit();
              }}
            >
              <Stack gap={4}>
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
                  className="mt-1 w-full"
                  loading={pending}
                  loadingLabel="正在登录…"
                >
                  登录
                </Button>
                <ErrorNotice message={errorMessage} />
              </Stack>
            </form>
          </Stack>
        </Card>
      </Section>
    </Container>
  );
}

export { LoginPageView, type LoginPageViewProps };
