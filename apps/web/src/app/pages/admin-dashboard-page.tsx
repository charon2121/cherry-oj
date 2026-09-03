import { Link } from '@tanstack/react-router';
import { ArrowRight, BookOpen, Users } from 'lucide-react';

import { Card, CardAction, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Container, Section } from '@/components/ui/layout';
import { Heading } from '@/components/ui/typography';

function AdminDashboardPage() {
  return (
    <Container className="max-w-none">
      <Section>
        <Heading level={1} className="sr-only">
          Dashboard
        </Heading>
        <div className="grid gap-[var(--ds-space-4)] md:grid-cols-2">
          <Link
            to="/admin/users"
            search={{ page: 1 }}
            className="focus-visible:outline-ring rounded-[var(--ds-radius-lg)] no-underline focus-visible:outline-2 focus-visible:outline-offset-2"
          >
            <Card interactive className="h-full">
              <CardHeader>
                <Users aria-hidden="true" className="size-5 text-[var(--ds-brand-foreground)]" />
                <CardTitle>管理用户账号</CardTitle>
                <CardDescription>创建账号、调整可用状态或重置用户密码。</CardDescription>
                <CardAction>
                  <ArrowRight aria-hidden="true" className="size-4 text-[var(--ds-fg-meta)]" />
                </CardAction>
              </CardHeader>
            </Card>
          </Link>
          <Link
            to="/admin/problems"
            search={{ page: 1, q: '', status: 'ALL' }}
            className="focus-visible:outline-ring rounded-[var(--ds-radius-lg)] no-underline focus-visible:outline-2 focus-visible:outline-offset-2"
          >
            <Card interactive className="h-full">
              <CardHeader>
                <BookOpen aria-hidden="true" className="size-5 text-[var(--ds-brand-foreground)]" />
                <CardTitle>管理题目</CardTitle>
                <CardDescription>查找题目、创建草稿并进入题目工作台。</CardDescription>
                <CardAction>
                  <ArrowRight aria-hidden="true" className="size-4 text-[var(--ds-fg-meta)]" />
                </CardAction>
              </CardHeader>
            </Card>
          </Link>
        </div>
      </Section>
    </Container>
  );
}

export { AdminDashboardPage };
