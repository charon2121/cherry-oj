import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, Link, Outlet } from '@tanstack/react-router';

import { SiteAppShell } from '@/app/shells/site-app-shell';
import { Card } from '@/components/ui/card';
import { Container, Section, Stack } from '@/components/ui/layout';
import { linkVariants } from '@/components/ui/link';
import { Heading, Text } from '@/components/ui/typography';

type RouterContext = {
  queryClient: QueryClient;
};

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
  notFoundComponent: NotFoundPage,
});

function RootLayout() {
  return <Outlet />;
}

function NotFoundPage() {
  return (
    <SiteAppShell>
      <Container>
        <Section>
          <Card padding="lg">
            <Stack gap={4}>
              <Text size="sm" tone="muted">
                404
              </Text>
              <Heading level={1} size="2xl">
                页面不存在
              </Heading>
              <Link to="/" className={linkVariants({ size: 'standalone' })}>
                返回首页
              </Link>
            </Stack>
          </Card>
        </Section>
      </Container>
    </SiteAppShell>
  );
}
