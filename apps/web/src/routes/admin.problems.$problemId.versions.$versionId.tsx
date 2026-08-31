import { createFileRoute } from '@tanstack/react-router';

import { AdminProblemWorkbench } from '@/features/problems/components/admin-problem-workbench';

export const Route = createFileRoute('/admin/problems/$problemId/versions/$versionId')({
  component: ProblemWorkbenchRoute,
});

function ProblemWorkbenchRoute() {
  const { problemId, versionId } = Route.useParams();
  return <AdminProblemWorkbench problemId={problemId} versionId={versionId} />;
}
