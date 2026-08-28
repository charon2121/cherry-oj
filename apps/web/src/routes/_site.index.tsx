import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';

import { InlineNotice } from '@/components/ui/inline-notice';
import { Container, Section, Stack } from '@/components/ui/layout';
import { Heading, Text } from '@/components/ui/typography';
import { sessionQueryOptions } from '@/features/auth/api/session-query';
import { SystemStatusPanel } from '@/features/system-status/components/system-status-panel';

export const Route = createFileRoute('/_site/')({
  component: HomePage,
});

export function HomePage() {
  const session = useQuery(sessionQueryOptions());
  return (
    <Container>
      <Section className="py-20">
        <Stack gap={4}>
          <Text size="sm" tone="primary" className="text-brand font-medium">
            Cherry OJ · Focused Workspace
          </Text>
          <Heading level={1} size="3xl">
            专注练习，清晰看到每一次进步
          </Heading>
          <Text className="max-w-2xl" tone="muted">
            账号、题目与提交都通过 Gateway 安全访问。浏览器只持有受保护的登录 Cookie。
          </Text>
        </Stack>
        {session.data?.authenticated && session.data.user.passwordChangeRequired ? (
          <InlineNotice className="mt-6" variant="warning" title="首次登录需要修改密码">
            完成修改前，受保护功能暂不可用。
          </InlineNotice>
        ) : null}
        <SystemStatusPanel />
      </Section>
    </Container>
  );
}
