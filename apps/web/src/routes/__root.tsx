import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, Link, Outlet } from '@tanstack/react-router';

import { SiteAppShell } from '@/app/shells/site-app-shell';
import { linkVariants } from '@/components/ui/link';
import { DetailPageTemplate } from '@/components/ui/page-templates';

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
      <DetailPageTemplate title="页面不存在" width="narrow">
        <Link to="/" className={linkVariants({ size: 'standalone' })}>
          返回首页
        </Link>
      </DetailPageTemplate>
    </SiteAppShell>
  );
}
