import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';

import { InlineNotice } from '@/components/ui/inline-notice';
import { Container, Section } from '@/components/ui/layout';
import { Heading } from '@/components/ui/typography';
import { sessionQueryOptions } from '@/features/auth/api/session-query';

export const Route = createFileRoute('/_site/')({
  component: HomePage,
});

export function HomePage() {
  const session = useQuery(sessionQueryOptions());
  return (
    <Container>
      <Section>
        <Heading level={1} className="sr-only">
          Cherry OJ 首页
        </Heading>
        {session.data?.authenticated && session.data.user.passwordChangeRequired ? (
          <InlineNotice variant="warning" title="首次登录需要修改密码">
            完成修改前，受保护功能暂不可用。
          </InlineNotice>
        ) : null}
      </Section>
    </Container>
  );
}
