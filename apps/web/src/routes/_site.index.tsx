import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowRight, BookOpen, LayoutDashboard } from 'lucide-react';
import { cloneElement, type ReactElement } from 'react';

import { InlineNotice } from '@/components/ui/inline-notice';
import { linkVariants } from '@/components/ui/link';
import { DetailPageTemplate } from '@/components/ui/page-templates';
import { sessionQueryOptions } from '@/features/auth/api/session-query';

export const Route = createFileRoute('/_site/')({
  component: HomePage,
});

type HomeEntryProps = Readonly<{
  icon: typeof BookOpen;
  link: ReactElement<{ className?: string }>;
  title: string;
}>;

function HomeEntry({ icon: Icon, link, title }: HomeEntryProps) {
  return cloneElement(
    link,
    {
      className:
        'hover:bg-surface-translucent-hover focus-visible:outline-ring duration-fast flex min-h-11 items-center gap-3 rounded-lg px-3 no-underline transition-colors focus-visible:outline-1 focus-visible:outline-offset-[-1px] motion-reduce:transition-none',
    },
    <>
      <Icon aria-hidden="true" className="text-fg-meta size-4 shrink-0" />
      <span className="text-foreground font-body min-w-0 flex-1 text-sm">{title}</span>
      <ArrowRight aria-hidden="true" className="text-fg-meta size-4 shrink-0" />
    </>,
  );
}

export function HomePage() {
  const session = useQuery(sessionQueryOptions());
  const isAdmin = session.data?.authenticated && session.data.user.role === 'ADMIN';
  return (
    <DetailPageTemplate title="Cherry OJ 首页" titleVisible={false} className="max-w-2xl">
      {session.data?.authenticated && session.data.user.passwordChangeRequired ? (
        <InlineNotice className="mb-4" variant="warning" title="首次登录需要修改密码">
          <Link to="/account/password" className={linkVariants()}>
            修改密码
          </Link>
        </InlineNotice>
      ) : null}
      <nav aria-label="首页入口" className="grid gap-px">
        <HomeEntry
          icon={BookOpen}
          title="浏览题库"
          link={<Link to="/problems" search={{ sort: 'UPDATED_DESC', size: 20 }} />}
        />
        {isAdmin ? (
          <HomeEntry icon={LayoutDashboard} title="进入管理中心" link={<Link to="/admin" />} />
        ) : null}
      </nav>
    </DetailPageTemplate>
  );
}
