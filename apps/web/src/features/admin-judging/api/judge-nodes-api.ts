import { z } from 'zod';

import type { JudgeNode, JudgeNodeListData } from '@/generated/api';
import { requestJson } from '@/lib/api/api-client';

const judgeNodeSchema = z.object({
  nodeId: z.string().regex(/^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$/),
  languages: z.array(z.string()).max(32),
  online: z.boolean(),
  lastSeenAt: z.string().datetime({ offset: true }),
}) satisfies z.ZodType<JudgeNode>;

const judgeNodeListSchema = z.object({
  items: z.array(judgeNodeSchema).max(1000),
}) satisfies z.ZodType<JudgeNodeListData>;

export const judgeNodeKeys = {
  all: ['admin-judge-nodes'] as const,
};

/** 已登记的判题节点与是否在线；判题节点每隔数秒续约，总览按分钟级刷新即可。 */
export async function listJudgeNodes(signal?: AbortSignal) {
  return (
    await requestJson('/api/admin/judging/judge-nodes', judgeNodeListSchema, {
      ...(signal === undefined ? {} : { signal }),
    })
  ).data.items;
}
