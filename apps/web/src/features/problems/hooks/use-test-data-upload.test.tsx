import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { expect, test, vi } from 'vitest';

import { ApiError } from '@/lib/api/api-client';

import { replaceTestData } from '../api/problems-api';
import { useTestDataUpload } from './use-test-data-upload';

vi.mock('../api/problems-api');

test('a superseded upload cannot clear the cancellation of its replacement', async () => {
  const replace = vi.mocked(replaceTestData);
  replace.mockImplementation(
    (_problemId, _file, signal) =>
      new Promise((_, reject) => {
        signal?.addEventListener('abort', () => reject(new ApiError('取消', { kind: 'aborted' })));
      }),
  );
  const client = new QueryClient();
  const onUploaded = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
  const { result } = renderHook(() => useTestDataUpload('problem-id', onUploaded), {
    wrapper: ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  });

  act(() => result.current.startUpload(new File(['first'], 'first.zip')));
  await waitFor(() => expect(replace).toHaveBeenCalledTimes(1));
  const [firstUpload] = client.getMutationCache().getAll();

  act(() => result.current.startUpload(new File(['second'], 'second.zip')));
  await waitFor(() => {
    expect(replace).toHaveBeenCalledTimes(2);
    expect(firstUpload?.state.status).toBe('error');
    expect(result.current.uploadMessage).toBe('正在上传并检查 ZIP…');
  });

  act(() => result.current.cancelUpload());
  await waitFor(() => {
    expect(result.current.isUploading).toBe(false);
    expect(result.current.uploadMessage).toBe('上传已取消。');
  });
  expect(onUploaded).not.toHaveBeenCalled();
});
