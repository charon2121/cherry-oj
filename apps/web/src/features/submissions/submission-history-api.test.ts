import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';

import { authKeys } from '@/features/auth/api/session-query';

import { historySearchSchema, sourceSchema, withHistoryIdentity } from './submission-history-api';

const userId = 'd0e35399-6487-4ac8-8138-8d5bd60eb003';
const session = {
  authenticated: true,
  user: { id: userId, status: 'ACTIVE', passwordChangeRequired: false },
};

describe('history identity and source boundaries', () => {
  it('rejects a late private response after switching account or logging out', async () => {
    const client = new QueryClient();
    client.setQueryData(authKeys.session(), session);
    let finish: (value: string) => void = () => undefined;
    const response = withHistoryIdentity(
      client,
      userId,
      () =>
        new Promise<string>((resolve) => {
          finish = resolve;
        }),
    );
    client.setQueryData(authKeys.session(), { authenticated: false });
    finish('private source');
    await expect(response).rejects.toMatchObject({ code: 'SESSION_CHANGED' });
    await expect(
      withHistoryIdentity(client, userId, () => Promise.resolve('private source')),
    ).rejects.toMatchObject({ code: 'SESSION_CHANGED' });
    client.clear();
  });
  it('keeps old recovery links and safely drops malformed history parameters', () => {
    expect(
      historySearchSchema.parse({
        submissionId: userId,
        tab: 'submissions',
        historyPage: '-2',
        historySubmissionId: 'invalid',
        historyVerdict: 'AC',
      }),
    ).toMatchObject({
      submissionId: userId,
      tab: 'submissions',
      historyPage: undefined,
      historySubmissionId: undefined,
      historyVerdict: 'AC',
    });
  });
  it('applies the raw UTF-8 limit, not just a character limit', () => {
    const data = {
      submissionId: userId,
      problemId: userId,
      problemVersionId: userId,
      languageId: 'cpp',
      source: '中'.repeat(90_000),
    };
    expect(sourceSchema.safeParse(data).success).toBe(false);
    expect(sourceSchema.safeParse({ ...data, source: '\n"int main() {}"' }).success).toBe(true);
  });
});
