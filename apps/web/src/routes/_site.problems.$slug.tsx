import { createFileRoute } from '@tanstack/react-router';

import { ProblemDetailPage } from '@/features/problems/components/problem-detail-page';

export const Route = createFileRoute('/_site/problems/$slug')({
  component: ProblemRoute,
});

function ProblemRoute() {
  return <ProblemDetailPage slug={Route.useParams().slug} />;
}
