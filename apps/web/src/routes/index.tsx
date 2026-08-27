import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';

import { sessionQueryOptions } from '@/features/auth/api/session-query';
import { SystemStatusPanel } from '@/features/system-status/components/system-status-panel';

export const Route = createFileRoute('/')({
  component: HomePage,
});

export function HomePage() {
  const session = useQuery(sessionQueryOptions());
  return (
    <section className="mx-auto flex max-w-6xl flex-col items-start px-4 py-20">
      <p className="text-primary text-sm font-medium">Cherry OJ · Focused Workspace</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight">专注练习，清晰看到每一次进步</h1>
      <p className="text-muted-foreground mt-4 max-w-2xl">
        账号、题目与提交都通过 Gateway 安全访问。浏览器只持有受保护的登录 Cookie。
      </p>
      {session.data?.authenticated && session.data.user.passwordChangeRequired ? (
        <div className="border-warning bg-warning-soft mt-6 w-full rounded-lg border p-4 text-left">
          <p className="font-medium">首次登录需要修改密码</p>
          <p className="mt-1 text-sm">完成修改前，受保护功能暂不可用。</p>
        </div>
      ) : null}
      <SystemStatusPanel />
    </section>
  );
}
