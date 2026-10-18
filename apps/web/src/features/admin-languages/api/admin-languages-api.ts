import { z } from 'zod';

import type { AdminLanguageSetting, UpdateLanguageSettingRequest } from '@/generated/api';
import { requestJson } from '@/lib/api/api-client';
import { withCsrf } from '@/lib/api/csrf';

const languageSchema = z.object({
  id: z.string().regex(/^[a-z][a-z0-9-]{0,31}$/),
  displayName: z.string().max(64),
  solutionPrefix: z.string().min(1).max(65536),
  updatedAt: z.string().min(1),
}) satisfies z.ZodType<AdminLanguageSetting>;

const languageListSchema = z.object({ items: z.array(languageSchema).max(100) });

export const adminLanguageKeys = {
  all: ['admin-languages'] as const,
};

export async function listLanguageSettings(signal?: AbortSignal) {
  const response = await requestJson('/api/admin/languages', languageListSchema, {
    ...(signal === undefined ? {} : { signal }),
  });
  return response.data.items;
}

export function updateLanguageSetting(languageId: string, request: UpdateLanguageSettingRequest) {
  return withCsrf(
    async (csrfToken) =>
      (
        await requestJson(`/api/admin/languages/${languageId}`, languageSchema, {
          method: 'PUT',
          body: request,
          csrfToken,
        })
      ).data,
  );
}
