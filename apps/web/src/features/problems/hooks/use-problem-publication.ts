import { useMutation, useQuery } from '@tanstack/react-query';

import { apiErrorMessage } from '@/lib/api/api-error-message';

import { getPublishCheck, problemKeys, publish, unpublish } from '../api/problems-api';

export function useProblemPublication(
  problemId: string,
  rowVersion: number,
  onVisibilityChanged: () => Promise<void>,
) {
  const check = useQuery({
    queryKey: problemKeys.publishCheck(problemId),
    queryFn: ({ signal }) => getPublishCheck(problemId, signal),
    refetchInterval: 10_000,
    retry: false,
  });

  const publishing = useMutation({
    mutationFn: () => publish(problemId, rowVersion),
    onSuccess: onVisibilityChanged,
  });
  const unpublishing = useMutation({
    mutationFn: () => unpublish(problemId, rowVersion),
    onSuccess: onVisibilityChanged,
  });
  const nodeUnavailable = check.data?.checks.find(
    (item) => item.code === 'ONLINE_JUDGE_NODE' && !item.passed,
  );
  const calibrationReady = Boolean(
    check.data?.checks.some((item) => item.code === 'CALIBRATION' && item.passed),
  );
  const passedChecks = check.data?.checks.filter((item) => item.passed).length ?? 0;
  const totalChecks = check.data?.checks.length ?? 0;

  return {
    check: check.data,
    isChecking: check.isPending,
    isRefreshingCheck: check.isFetching,
    checkError: check.isError
      ? apiErrorMessage(check.error, '公开检查失败，请刷新题目状态。')
      : undefined,
    refreshCheck: () => void check.refetch(),
    nodeUnavailable,
    calibrationReady,
    passedChecks,
    totalChecks,
    isChangingVisibility: publishing.isPending || unpublishing.isPending,
    actionError:
      publishing.isError || unpublishing.isError
        ? apiErrorMessage(publishing.error ?? unpublishing.error, '操作失败，题目状态保持不变。')
        : undefined,
    publish: (onSuccess: () => void) => publishing.mutate(undefined, { onSuccess }),
    unpublish: (onSuccess: () => void) => unpublishing.mutate(undefined, { onSuccess }),
  };
}
