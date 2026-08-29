import { Button } from '@/components/ui/button';
import { Field, Input } from '@/components/ui/field';
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

function WorkspaceWordmark() {
  return (
    <div
      aria-hidden="true"
      data-testid="login-workspace-art"
      className="relative hidden h-full min-h-0 min-w-0 lg:block"
    >
      <img
        alt=""
        className="absolute top-[var(--ds-space-8)] left-[calc(var(--ds-space-12)+var(--ds-space-12))] h-[calc(100%-var(--ds-space-8))] w-auto max-w-[calc(100%-var(--ds-space-8))] object-contain object-left-top mix-blend-difference"
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
      <Section className="box-border grid h-full min-h-0 pt-[var(--ds-space-8)] pb-0 lg:pt-[var(--ds-space-12)] [@media(max-height:800px)]:lg:pt-[var(--ds-space-8)]">
        <div className="grid h-full min-h-0 items-start gap-[var(--ds-space-12)] lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
          <div className="w-full max-w-xl justify-self-center lg:justify-self-start lg:pt-[calc(var(--ds-space-12)+var(--ds-space-12))] [@media(max-height:800px)]:lg:pt-[var(--ds-space-8)]">
            <Stack gap={8}>
              <Stack gap={3}>
                <Text size="sm" tone="muted">
                  安全登录
                </Text>
                <Heading level={1} size="3xl">
                  登录 Cherry OJ
                </Heading>
                <span
                  className="border-primary w-[var(--ds-space-6)] border-t-2"
                  aria-hidden="true"
                />
                <Text size="sm" tone="muted">
                  账号由管理员开通，密码不会保存在浏览器中。
                </Text>
              </Stack>

              <form
                className="pt-[var(--ds-space-2)]"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (!pending) onSubmit();
                }}
              >
                <Stack gap={8}>
                  <Field label="用户名" required>
                    <Input
                      id="username"
                      name="username"
                      autoComplete="username"
                      minLength={3}
                      maxLength={64}
                      pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,63}"
                      placeholder="请输入用户名"
                      className="min-h-[var(--ds-space-12)]"
                      value={username}
                      onChange={(event) => onUsernameChange(event.target.value)}
                    />
                  </Field>
                  <div className="[&_input]:min-h-[var(--ds-space-12)]">
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
                    className="min-h-[var(--ds-space-12)] w-full"
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
