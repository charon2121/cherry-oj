import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowRight, BookOpen, LayoutDashboard } from 'lucide-react';

import { Card, CardAction, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { InlineNotice } from '@/components/ui/inline-notice';
import { Container, Section, Stack } from '@/components/ui/layout';
import { Heading } from '@/components/ui/typography';
import { sessionQueryOptions } from '@/features/auth/api/session-query';

export const Route = createFileRoute('/_site/')({
  component: HomePage,
});

export function HomePage() {
  const session = useQuery(sessionQueryOptions());
  return (
    <Container>
      <Section>
        <Heading level={1} className="sr-only">
          Cherry OJ 首页
        </Heading>
        <Stack gap={6}>
          {session.data?.authenticated && session.data.user.passwordChangeRequired ? (
            <InlineNotice variant="warning" title="首次登录需要修改密码">
              完成修改前，受保护功能暂不可用。
            </InlineNotice>
          ) : null}

          <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
            <Link
              to="/problems"
              search={{ sort: 'UPDATED_DESC', size: 20 }}
              className="focus-visible:outline-ring rounded-lg no-underline focus-visible:outline-1 focus-visible:outline-offset-0"
            >
              <Card interactive elevated padding="lg" className="h-full min-h-56 justify-center">
                <CardHeader>
                  <BookOpen aria-hidden="true" className="text-brand size-5" />
                  <CardTitle className="font-regular text-xl">
                    {session.data?.authenticated ? '继续进入题库' : '浏览公开题库'}
                  </CardTitle>
                  <CardDescription className="text-15 max-w-xl">
                    查找题目，打开详情后编写并提交你的解答。
                  </CardDescription>
                  <CardAction>
                    <ArrowRight aria-hidden="true" className="text-fg-meta size-5" />
                  </CardAction>
                </CardHeader>
              </Card>
            </Link>

            {session.data?.authenticated && session.data.user.role === 'ADMIN' ? (
              <Link
                to="/admin"
                className="focus-visible:outline-ring rounded-lg no-underline focus-visible:outline-1 focus-visible:outline-offset-0"
              >
                <Card interactive padding="lg" className="h-full min-h-56 justify-center">
                  <CardHeader>
                    <LayoutDashboard aria-hidden="true" className="text-brand size-5" />
                    <CardTitle>进入管理中心</CardTitle>
                    <CardDescription>管理账号与题目内容。</CardDescription>
                    <CardAction>
                      <ArrowRight aria-hidden="true" className="text-fg-meta size-4" />
                    </CardAction>
                  </CardHeader>
                </Card>
              </Link>
            ) : (
              <Card padding="lg" className="min-h-56 justify-center">
                <CardHeader>
                  <CardTitle>清晰的判题反馈</CardTitle>
                  <CardDescription>提交后查看运行状态、耗时与最终判定。</CardDescription>
                </CardHeader>
              </Card>
            )}
          </div>
        </Stack>
      </Section>
    </Container>
  );
}
