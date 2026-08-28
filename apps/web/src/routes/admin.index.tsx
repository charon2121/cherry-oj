import { createFileRoute } from '@tanstack/react-router';

import { AdminDashboardPage } from '@/app/pages/admin-dashboard-page';

export const Route = createFileRoute('/admin/')({
  component: AdminDashboardPage,
});
