import { createFileRoute } from '@tanstack/react-router';

import { AdminDashboardPage } from '@/app/pages/admin-dashboard-page';

// 保留早期拼写别名路径 `/admin/dashborad` 以兼容既有书签与冒烟用例，主入口为 `/admin`（`admin.index.tsx`）。
export const Route = createFileRoute('/admin/dashborad')({
  component: AdminDashboardPage,
});
