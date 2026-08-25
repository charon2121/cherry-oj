import { createFileRoute } from '@tanstack/react-router';

import { SystemStatusPanel } from '@/features/system-status/components/system-status-panel';

export const Route = createFileRoute('/')({
  component: HomePage,
});

export function HomePage() {
  return (
    <section className="mx-auto flex max-w-6xl flex-col items-start px-4 py-20">
      <p className="text-primary text-sm font-medium">M4 · Web Skeleton</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight">Cherry OJ 前端骨架已就绪</h1>
      <p className="text-muted-foreground mt-4 max-w-2xl">
        Router 管 URL，Query 管服务端状态，浏览器只通过 Gateway 的 /api 访问后端。
      </p>
      <SystemStatusPanel />
    </section>
  );
}
