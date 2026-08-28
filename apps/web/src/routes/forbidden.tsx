import { createFileRoute, Link } from '@tanstack/react-router';

import { Container } from '@/components/ui/layout';
import { linkVariants } from '@/components/ui/link';
import { Heading, Text } from '@/components/ui/typography';

export const Route = createFileRoute('/forbidden')({ component: ForbiddenPage });

function ForbiddenPage() {
  return (
    <Container as="section" className="max-w-3xl py-16">
      <Text size="sm" tone="primary" className="text-danger font-medium">
        403 · 无权访问
      </Text>
      <Heading level={1} size="2xl" className="mt-2">
        当前账号不能打开这个页面
      </Heading>
      <Text tone="muted" className="mt-3">
        如果你认为权限配置有误，请联系管理员。
      </Text>
      <Link to="/" className={linkVariants({ size: 'standalone', className: 'mt-6' })}>
        返回首页
      </Link>
    </Container>
  );
}
