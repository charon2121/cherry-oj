import { createFileRoute } from '@tanstack/react-router';
import { z } from 'zod';

import {
  AdminProblemWorkbench,
  type ProblemWorkbenchPanel,
} from '@/features/problems/components/admin-problem-workbench';

// 工作台是一页到底的编辑页；panel 只记录当前打开的对话框（测试数据、校准、公开），便于直接链接。
const panelSchema = z.object({
  panel: z.enum(['test-data', 'calibration', 'publish']).optional().catch(undefined),
});

export const Route = createFileRoute('/admin/problems/$problemId')({
  validateSearch: panelSchema,
  component: ProblemWorkbenchRoute,
});

function ProblemWorkbenchRoute() {
  const { problemId } = Route.useParams();
  const { panel } = Route.useSearch();
  const navigate = Route.useNavigate();
  return (
    <AdminProblemWorkbench
      problemId={problemId}
      panel={panel}
      onPanelChange={(next: ProblemWorkbenchPanel | undefined) =>
        void navigate({ search: { panel: next }, replace: true })
      }
    />
  );
}
