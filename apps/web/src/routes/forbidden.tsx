import { createFileRoute, Link } from '@tanstack/react-router';

export const Route = createFileRoute('/forbidden')({ component: ForbiddenPage });

function ForbiddenPage() {
  return (
    <section className="mx-auto max-w-3xl px-4 py-16">
      <p className="text-danger text-sm font-medium">403 · 无权访问</p>
      <h1 className="mt-2 text-2xl font-semibold">当前账号不能打开这个页面</h1>
      <p className="text-muted-foreground mt-3">如果你认为权限配置有误，请联系管理员。</p>
      <Link to="/" className="mt-6 inline-block underline underline-offset-4">
        返回首页
      </Link>
    </section>
  );
}
