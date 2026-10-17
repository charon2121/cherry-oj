import type { ProblemCodeMode } from '@/generated/api';

/** 代码模式在题库筛选、做题页和管理端都用同一个叫法。 */
export const codeModeLabels: Record<ProblemCodeMode, string> = {
  ACM: 'ACM',
  CORE: '核心代码',
};
