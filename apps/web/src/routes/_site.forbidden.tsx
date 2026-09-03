import { createFileRoute, Link } from '@tanstack/react-router';

import { Card } from '@/components/ui/card';
import { Container, Section, Stack } from '@/components/ui/layout';
import { linkVariants } from '@/components/ui/link';
import { Heading, Text } from '@/components/ui/typography';

export const Route = createFileRoute('/_site/forbidden')({ component: ForbiddenPage });

function ForbiddenPage() {
  return (
    <Container className="max-w-3xl">
      <Section>
        <Card padding="lg">
          <Stack gap={4}>
            <Text size="sm" tone="primary" className="text-danger font-[var(--ds-weight-body)]">
              403 · 无权访问
            </Text>
            <Heading level={1} size="2xl">
              当前账号不能打开这个页面
            </Heading>
            <Text tone="muted">如果你认为权限配置有误，请联系管理员。</Text>
            <Link to="/" className={linkVariants({ size: 'standalone' })}>
              返回首页
            </Link>
          </Stack>
        </Card>
      </Section>
    </Container>
  );
}
