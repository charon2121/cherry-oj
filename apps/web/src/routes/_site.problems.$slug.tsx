import { createFileRoute } from '@tanstack/react-router';
import { z } from 'zod';

import { ProblemDetailPage } from '@/features/problems/components/problem-detail-page';

export const Route = createFileRoute('/_site/problems/$slug')({
  validateSearch: z.object({ submissionId: z.string().uuid().optional().catch(undefined) }),
  component: ProblemRoute,
});

function ProblemRoute() {
  return <ProblemDetailPage slug={Route.useParams().slug} />;
}
