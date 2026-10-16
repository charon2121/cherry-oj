import { createFileRoute } from '@tanstack/react-router';

import { AdminUsersPage } from '@/features/admin-users/components/admin-users-page';

export const Route = createFileRoute('/admin/users')({
  validateSearch: (search: Record<string, unknown>) => ({
    page:
      typeof search.page === 'number' && Number.isInteger(search.page) && search.page > 0
        ? search.page
        : 1,
  }),
  component: AdminUsersRoute,
});

function AdminUsersRoute() {
  const { page } = Route.useSearch();
  return <AdminUsersPage page={page} />;
}
