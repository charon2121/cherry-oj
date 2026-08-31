import { createFileRoute } from '@tanstack/react-router';
import { z } from 'zod';

import { AdminProblemListPage } from '@/features/problems/components/admin-problem-list-page';

const searchSchema = z.object({
  page: z.coerce.number().int().min(1).catch(1).default(1),
  q: z.string().max(100).catch('').default(''),
  status: z.enum(['ALL', 'ACTIVE', 'ARCHIVED']).catch('ALL').default('ALL'),
});

export const Route = createFileRoute('/admin/problems/')({
  validateSearch: searchSchema,
  component: AdminProblemsRoute,
});

function AdminProblemsRoute() {
  const search = Route.useSearch();
  const navigate = Route.useNavigate();
  return (
    <AdminProblemListPage
      key={JSON.stringify(search)}
      search={search}
      navigate={(next) => void navigate({ search: next })}
    />
  );
}
