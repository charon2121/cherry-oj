import { createFileRoute } from '@tanstack/react-router';

import { ProblemDetailPage } from '@/features/problems/components/problem-detail-page';
import { historySearchSchema } from '@/features/submissions/submission-history-api';

export const Route = createFileRoute('/_site/problems/$slug')({
  validateSearch: historySearchSchema,
  component: ProblemRoute,
});

function ProblemRoute() {
  return <ProblemDetailPage slug={Route.useParams().slug} />;
}
