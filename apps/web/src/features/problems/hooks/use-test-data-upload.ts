import { useMutation } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';

import { ApiError } from '@/lib/api/api-client';
import { apiErrorMessage } from '@/lib/api/api-error-message';

import { replaceTestData } from '../api/problems-api';

export function useTestDataUpload(problemId: string, refreshTestData: () => Promise<void>) {
  const activeUpload = useRef<AbortController | undefined>(undefined);
  const [uploadMessage, setUploadMessage] = useState<string>();

  useEffect(
    () => () => {
      activeUpload.current?.abort();
      activeUpload.current = undefined;
    },
    [],
  );

  const upload = useMutation({
    mutationFn: ({ file, controller }: { file: File; controller: AbortController }) =>
      replaceTestData(problemId, file, controller.signal),
    onSuccess: async (uploaded, { controller }) => {
      if (activeUpload.current === controller) {
        activeUpload.current = undefined;
        setUploadMessage(
          `已替换测试数据，共 ${uploaded.testcaseCount} 组。旧的标定已过期，请重新标定。`,
        );
      }
      await refreshTestData();
    },
    onError: async (error, { controller }) => {
      if (activeUpload.current !== controller) return;
      activeUpload.current = undefined;
      if (error instanceof ApiError && error.kind === 'aborted') {
        setUploadMessage('上传已取消。');
        return;
      }
      const message = apiErrorMessage(error, '上传失败。');
      if (error instanceof ApiError && (error.kind !== 'http' || (error.status ?? 500) >= 500)) {
        setUploadMessage(`${message} 请核对当前测试数据。`);
        await refreshTestData();
      } else {
        setUploadMessage(message);
      }
    },
  });

  function startUpload(file: File) {
    activeUpload.current?.abort();
    const controller = new AbortController();
    activeUpload.current = controller;
    setUploadMessage('正在上传并检查 ZIP…');
    upload.mutate({ file, controller });
  }

  function cancelUpload() {
    activeUpload.current?.abort();
  }

  return { isUploading: upload.isPending, uploadMessage, startUpload, cancelUpload };
}
