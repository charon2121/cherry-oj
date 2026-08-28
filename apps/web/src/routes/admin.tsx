import { createFileRoute } from '@tanstack/react-router';

import { AdminAppShell } from '@/app/shells/admin-app-shell';
import { Container } from '@/components/ui/layout';
import { Heading, Text } from '@/components/ui/typography';
import { requireAdmin } from '@/features/auth/lib/route-guards';

export const Route = createFileRoute('/admin')({
  beforeLoad: ({ context, location }) => requireAdmin(context.queryClient, location.href),
  component: AdminAppShell,
  notFoundComponent: AdminNotFoundPage,
});

function AdminNotFoundPage() {
  return (
    <Container as="section" className="max-w-none py-16">
      <Text size="sm" tone="muted">
        404
      </Text>
      <Heading level={1} size="2xl" className="mt-2">
        管理页面不存在
      </Heading>
    </Container>
  );
}
