import { Button } from '@/components/ui/button';
import { FormField } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Container, Section, Stack } from '@/components/ui/layout';
import { Eyebrow, Heading, Text } from '@/components/ui/typography';
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

function WorkspaceWordmark() {
  return (
    <div
      aria-hidden="true"
      data-testid="login-workspace-art"
      className="border-border-soft relative hidden h-full min-h-0 min-w-0 border-l lg:block"
    >
      <img
        alt=""
        className="absolute top-0 left-[calc(var(--space-12)+var(--space-12))] h-full w-auto max-w-[calc(100%-var(--space-8))] object-contain object-left-top opacity-90 mix-blend-difference"
        src="/login-workspace-art.png"
      />
    </div>
  );
}

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
    <Container className="h-0 min-h-full">
      <Section className="box-border grid h-full min-h-0 pb-0">
        <div className="grid h-full min-h-0 items-start gap-12 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
          <div className="w-full max-w-xl justify-self-center lg:justify-self-start lg:pt-12">
            <Stack gap={8}>
              <Stack gap={3}>
                <Eyebrow tone="accent">安全登录</Eyebrow>
                <Heading level={1} size="3xl">
                  登录 Cherry OJ
                </Heading>
                <Text size="md" tone="muted" className="max-w-md">
                  账号由管理员开通，密码不会保存在浏览器中。
                </Text>
              </Stack>

              <form
                className="border-border-soft border-t pt-6"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (!pending) onSubmit();
                }}
              >
                <Stack gap={6}>
                  <FormField label="用户名" required>
                    <Input
                      id="username"
                      name="username"
                      autoComplete="username"
                      minLength={3}
                      maxLength={64}
                      pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,63}"
                      placeholder="请输入用户名"
                      className="min-h-12"
                      value={username}
                      onChange={(event) => onUsernameChange(event.target.value)}
                    />
                  </FormField>
                  <div className="[&_input]:min-h-12">
                    <PasswordField
                      id="password"
                      label="密码"
                      autoComplete="current-password"
                      value={password}
                      onChange={onPasswordChange}
                    />
                  </div>
                  <Button
                    type="submit"
                    size="md"
                    className="min-h-12 w-full"
                    loading={pending}
                    loadingLabel="正在登录…"
                  >
                    登录
                  </Button>
                </Stack>
                <ErrorNotice message={errorMessage} />
              </form>
            </Stack>
          </div>

          <WorkspaceWordmark />
        </div>
      </Section>
    </Container>
  );
}

export { LoginPageView, type LoginPageViewProps };
