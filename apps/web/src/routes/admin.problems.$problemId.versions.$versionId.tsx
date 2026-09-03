import { createFileRoute } from '@tanstack/react-router';
import { z } from 'zod';

import {
  AdminProblemWorkbench,
  type ProblemWorkbenchStep,
} from '@/features/problems/components/admin-problem-workbench';

const stepSchema = z.object({
  step: z
    .enum(['basic', 'statement', 'samples', 'starter-code', 'test-and-calibrate', 'publish'])
    .catch('basic')
    .default('basic'),
});

export const Route = createFileRoute('/admin/problems/$problemId/versions/$versionId')({
  validateSearch: stepSchema,
  component: ProblemWorkbenchRoute,
});

function ProblemWorkbenchRoute() {
  const { problemId, versionId } = Route.useParams();
  const { step } = Route.useSearch();
  const navigate = Route.useNavigate();
  return (
    <AdminProblemWorkbench
      problemId={problemId}
      versionId={versionId}
      step={step}
      onStepChange={(nextStep: ProblemWorkbenchStep) =>
        void navigate({ search: { step: nextStep } })
      }
    />
  );
}
