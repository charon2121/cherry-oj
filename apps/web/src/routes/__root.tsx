import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, Link, Outlet } from '@tanstack/react-router';

type RouterContext = {
  queryClient: QueryClient;
};

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
  notFoundComponent: NotFoundPage,
});

function RootLayout() {
  return (
    <div className="bg-background text-foreground min-h-svh">
      <header className="border-border bg-sidebar border-b">
        <nav aria-label="主导航" className="mx-auto flex h-12 max-w-6xl items-center px-4">
          <Link to="/" className="font-semibold tracking-tight">
            <span className="text-brand">Cherry</span> OJ
          </Link>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}

function NotFoundPage() {
  return (
    <section className="mx-auto max-w-6xl px-4 py-16">
      <p className="text-muted-foreground text-sm">404</p>
      <h1 className="mt-2 text-2xl font-semibold">页面不存在</h1>
      <Link to="/" className="text-primary mt-6 inline-block underline-offset-4 hover:underline">
        返回首页
      </Link>
    </section>
  );
}
