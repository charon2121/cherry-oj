import { createFileRoute, Link } from '@tanstack/react-router';

import { linkVariants } from '@/components/ui/link';
import { DetailPageTemplate } from '@/components/ui/page-templates';

export const Route = createFileRoute('/_site/forbidden')({ component: ForbiddenPage });

function ForbiddenPage() {
  return (
    <DetailPageTemplate title="无权访问" width="narrow">
      <Link to="/" className={linkVariants({ size: 'standalone' })}>
        返回首页
      </Link>
    </DetailPageTemplate>
  );
}
