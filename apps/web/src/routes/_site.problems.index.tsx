import { createFileRoute } from '@tanstack/react-router';
import { z } from 'zod';

import { ProblemListPage } from '@/features/problems/components/problem-list-page';

const searchSchema = z.object({
  q: z.string().min(1).max(100).optional().catch(undefined),
  difficulty: z.enum(['UNRATED', 'EASY', 'MEDIUM', 'HARD']).optional().catch(undefined),
  tag: z
    .union([z.string(), z.array(z.string())])
    .optional()
    .transform((value) =>
      value === undefined ? undefined : (Array.isArray(value) ? value : [value]).slice(0, 10),
    ),
  codeMode: z.enum(['ACM', 'CORE']).optional().catch(undefined),
  language: z
    .string()
    .regex(/^[a-z][a-z0-9-]{0,31}$/)
    .optional()
    .catch(undefined),
  sort: z
    .enum(['UPDATED_DESC', 'UPDATED_ASC', 'TITLE_ASC'])
    .catch('UPDATED_DESC')
    .default('UPDATED_DESC'),
  cursor: z.string().min(1).max(2048).optional().catch(undefined),
  size: z.coerce.number().int().min(1).max(100).catch(20).default(20),
});

export const Route = createFileRoute('/_site/problems/')({
  validateSearch: searchSchema,
  component: ProblemsRoute,
});

function ProblemsRoute() {
  const search = Route.useSearch();
  const navigate = Route.useNavigate();
  return (
    <ProblemListPage
      key={JSON.stringify(search)}
      search={search}
      navigate={(next) => void navigate({ search: next })}
    />
  );
}
