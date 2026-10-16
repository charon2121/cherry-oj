import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowRight, BookOpen, LayoutDashboard } from 'lucide-react';
import { cloneElement, type ReactElement } from 'react';

import { InlineNotice } from '@/components/ui/inline-notice';
import { Heading } from '@/components/ui/typography';
import { sessionQueryOptions } from '@/features/auth/api/session-query';

export const Route = createFileRoute('/_site/')({
  component: HomePage,
});

type HomeEntryProps = Readonly<{
  description: string;
  icon: typeof BookOpen;
  link: ReactElement<{ className?: string }>;
  title: string;
}>;

// 入口是两行可点的条目（linear.app 的列表行：16px 图标、13px/500 标题、12px 灰色说明），
// 不是带阴影的大卡片，也不放没有操作价值的宣传卡。
function HomeEntry({ description, icon: Icon, link, title }: HomeEntryProps) {
  return cloneElement(
    link,
    {
      className:
        'group hover:bg-surface-translucent-hover focus-visible:outline-ring duration-fast flex min-h-14 items-center gap-3 rounded-lg px-3 no-underline transition-colors focus-visible:outline-1 focus-visible:outline-offset-[-1px] motion-reduce:transition-none',
    },
    <>
      <Icon aria-hidden="true" className="text-fg-meta size-4 shrink-0" />
      <span className="min-w-0 flex-1">
        <span className="text-foreground font-body block text-sm">{title}</span>
        <span className="text-fg-meta text-cap block">{description}</span>
      </span>
      <ArrowRight aria-hidden="true" className="text-fg-meta size-4 shrink-0" />
    </>,
  );
}

export function HomePage() {
  const session = useQuery(sessionQueryOptions());
  const isAdmin = session.data?.authenticated && session.data.user.role === 'ADMIN';
  return (
    <div className="mx-auto w-full max-w-[56rem] px-4 pt-6 pb-12">
      <Heading level={1} className="sr-only">
        Cherry OJ 首页
      </Heading>
      {session.data?.authenticated && session.data.user.passwordChangeRequired ? (
        <InlineNotice className="mb-4" variant="warning" title="首次登录需要修改密码">
          完成修改前，受保护功能暂不可用。
        </InlineNotice>
      ) : null}
      <nav aria-label="首页入口" className="grid gap-px">
        <HomeEntry
          icon={BookOpen}
          title={session.data?.authenticated ? '继续进入题库' : '浏览公开题库'}
          description="查找题目，打开详情后编写并提交你的解答。"
          link={<Link to="/problems" search={{ sort: 'UPDATED_DESC', size: 20 }} />}
        />
        {isAdmin ? (
          <HomeEntry
            icon={LayoutDashboard}
            title="进入管理中心"
            description="处理待公开的题目与待首次改密的账号。"
            link={<Link to="/admin" />}
          />
        ) : null}
      </nav>
    </div>
  );
}
