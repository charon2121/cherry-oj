import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowRight, BookOpen, LayoutDashboard } from 'lucide-react';
import { cloneElement, type ReactElement } from 'react';

import { InlineNotice } from '@/components/ui/inline-notice';
import { Stack } from '@/components/ui/layout';
import { linkVariants } from '@/components/ui/link';
import { DetailPageTemplate } from '@/components/ui/page-templates';
import { Eyebrow, Text } from '@/components/ui/typography';
import { sessionQueryOptions } from '@/features/auth/api/session-query';
import { cn } from '@/lib/utils';

export const Route = createFileRoute('/_site/')({
  component: HomePage,
});

type HomeEntryProps = Readonly<{
  accent?: 'brand' | 'neutral';
  cta: string;
  description: string;
  icon: typeof BookOpen;
  link: ReactElement<{ className?: string }>;
  title: string;
}>;

function HomeEntry({
  accent = 'brand',
  cta,
  description,
  icon: Icon,
  link,
  title,
}: HomeEntryProps) {
  return cloneElement(
    link,
    {
      className:
        'group border-border bg-panel shadow-subtle hover:border-border-strong hover:bg-surface-hover focus-visible:outline-ring duration-fast flex min-h-40 flex-col justify-between gap-5 rounded-xl border p-6 no-underline transition-[background-color,border-color,box-shadow] focus-visible:outline-1 focus-visible:outline-offset-[-1px] motion-reduce:transition-none',
    },
    <>
      <span className="flex flex-col gap-3">
        <span
          className={cn(
            'flex size-10 shrink-0 items-center justify-center rounded-xl border',
            accent === 'brand'
              ? 'border-brand-border bg-brand-soft text-brand'
              : 'border-border bg-surface-subtle text-fg-2',
          )}
        >
          <Icon aria-hidden="true" className="size-5" />
        </span>
        <span className="space-y-1.5">
          <span className="text-foreground font-heading block text-base">{title}</span>
          <span className="text-fg-muted block text-sm leading-relaxed">{description}</span>
        </span>
      </span>
      <span className="text-fg-2 group-hover:text-foreground inline-flex items-center gap-1.5 text-xs font-medium">
        <span>{cta}</span>
        <ArrowRight
          aria-hidden="true"
          className="duration-fast size-3.5 shrink-0 transition-transform group-hover:translate-x-0.5 motion-reduce:transition-none"
        />
      </span>
    </>,
  );
}

export function HomePage() {
  const session = useQuery(sessionQueryOptions());
  const isAdmin = session.data?.authenticated && session.data.user.role === 'ADMIN';
  return (
    <DetailPageTemplate title="Cherry OJ 首页" titleVisible={false} className="max-w-4xl">
      <Stack gap={8} className="py-2 sm:py-6">
        {session.data?.authenticated && session.data.user.passwordChangeRequired ? (
          <InlineNotice variant="warning" title="首次登录需要修改密码">
            <Link to="/account/password" className={linkVariants()}>
              修改密码
            </Link>
          </InlineNotice>
        ) : null}

        <div className="space-y-2.5">
          <Eyebrow tone="accent">Cherry OJ</Eyebrow>
          <p className="font-display font-heading text-foreground tracking-heading text-2xl sm:text-3xl">
            算法训练与在线评测工作台
          </p>
          <Text tone="muted" className="max-w-xl">
            清晰阅读题面、在线编辑多语言代码、即时验证样例输入并追踪每一次评测结论。
          </Text>
        </div>

        <nav
          aria-label="首页入口"
          className={cn('grid gap-4', isAdmin ? 'sm:grid-cols-2' : 'max-w-xl')}
        >
          <HomeEntry
            accent="brand"
            icon={BookOpen}
            title="浏览题库"
            description="按难度、作答模式与编程语言筛选公开题目，进入分栏工作台编写、自测与提交代码。"
            cta="进入题库"
            link={<Link to="/problems" search={{ sort: 'UPDATED_DESC', size: 20 }} />}
          />
          {isAdmin ? (
            <HomeEntry
              accent="neutral"
              icon={LayoutDashboard}
              title="进入管理中心"
              description="查看判题节点在线状态，管理题面与测试数据包、执行标定并维护平台账号。"
              cta="打开管理中心"
              link={<Link to="/admin" />}
            />
          ) : null}
        </nav>
      </Stack>
    </DetailPageTemplate>
  );
}
