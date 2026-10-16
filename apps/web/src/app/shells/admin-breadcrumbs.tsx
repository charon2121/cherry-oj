import { Link, useLocation } from '@tanstack/react-router';

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';
import { useHeaderSlot } from '@/components/ui/header-slot';

import { adminBreadcrumbsFor, type AdminNavigationLeaf } from './admin-navigation-model';

// 面包屑替代此前写死的「管理中心」：那四个字在任何页面都一样，等于没有信息。
// 层级从导航模型推出，改分组名时两处同时生效（见 adminBreadcrumbsFor）。
function SectionLink({ label, to }: { label: string; to: AdminNavigationLeaf['to'] }) {
  if (to === '/admin/problems') {
    return (
      <Link to={to} search={{ page: 1, q: '', status: 'ALL' }}>
        {label}
      </Link>
    );
  }
  if (to === '/admin/users')
    return (
      <Link to={to} search={{ page: 1 }}>
        {label}
      </Link>
    );
  return <Link to={to}>{label}</Link>;
}

function AdminBreadcrumbs({ className }: { className?: string }) {
  const { pathname } = useLocation();
  const pageCrumb = useHeaderSlot()?.pageCrumb ?? null;
  const trail = adminBreadcrumbsFor(pathname, pageCrumb);

  return (
    <Breadcrumb className={className}>
      <BreadcrumbList>
        <BreadcrumbItem>
          {trail.length === 0 ? (
            <BreadcrumbPage>管理中心</BreadcrumbPage>
          ) : (
            <BreadcrumbLink render={<Link to="/admin">管理中心</Link>} />
          )}
        </BreadcrumbItem>
        {trail.map((crumb, index) => (
          <BreadcrumbItem key={crumb.label}>
            <BreadcrumbSeparator />
            {index === trail.length - 1 ? (
              <BreadcrumbPage className="max-w-80 truncate">{crumb.label}</BreadcrumbPage>
            ) : crumb.to !== undefined ? (
              <BreadcrumbLink render={<SectionLink label={crumb.label} to={crumb.to} />} />
            ) : (
              // 中间层是分组，没有自己的落地页，因此不做成链接——
              // 可点却点不到任何地方比不可点更糟。
              <span>{crumb.label}</span>
            )}
          </BreadcrumbItem>
        ))}
      </BreadcrumbList>
    </Breadcrumb>
  );
}

export { AdminBreadcrumbs };
