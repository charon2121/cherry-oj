import { queryOptions } from '@tanstack/react-query';
import { z } from 'zod';

import type { SystemStatusData } from '@/generated/api';
import { requestJson } from '@/lib/api/api-client';

export type SystemStatus = SystemStatusData;

const systemStatusSchema: z.ZodType<SystemStatus> = z
  .object({
    service: z.literal('gateway-service'),
    status: z.literal('ready'),
  })
  .loose();

async function getSystemStatus(signal: AbortSignal) {
  const response = await requestJson('/api/status', systemStatusSchema, { signal });
  return response.data;
}

export const systemStatusQueryOptions = queryOptions({
  queryKey: ['system-status'],
  queryFn: ({ signal }) => getSystemStatus(signal),
});
