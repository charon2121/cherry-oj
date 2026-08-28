import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, Link, Outlet } from '@tanstack/react-router';

import { SiteAppShell } from '@/app/shells/site-app-shell';
import { Container } from '@/components/ui/layout';
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
      <Container as="section" className="py-16">
        <Text size="sm" tone="muted">
          404
        </Text>
        <Heading level={1} size="2xl" className="mt-2">
          页面不存在
        </Heading>
        <Link to="/" className={linkVariants({ size: 'standalone', className: 'mt-6' })}>
          返回首页
        </Link>
      </Container>
    </SiteAppShell>
  );
}
