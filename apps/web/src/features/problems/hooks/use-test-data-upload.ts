import { useMutation } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';

import { ApiError } from '@/lib/api/api-client';
import { apiErrorMessage } from '@/lib/api/api-error-message';

import { replaceTestData } from '../api/problems-api';

export function useTestDataUpload(problemId: string, onUploaded: () => Promise<void>) {
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
      await onUploaded();
    },
    onError: (error, { controller }) => {
      if (activeUpload.current !== controller) return;
      activeUpload.current = undefined;
      setUploadMessage(
        error instanceof ApiError && error.kind === 'aborted'
          ? '上传已取消。'
          : apiErrorMessage(error, '上传或 ZIP 检查失败；原有测试数据保持不变。'),
      );
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
