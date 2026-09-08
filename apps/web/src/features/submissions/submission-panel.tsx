import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearch } from '@tanstack/react-router';
import { Play, Upload } from 'lucide-react';
import { type ReactNode, useEffect, useRef, useState } from 'react';

import { Button } from '@/components/ui/button';
import type { ProblemDetail, SubmissionData } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';

import {
  loadRecovery,
  newSubmissionKey,
  recoveryKey,
  type SubmissionRecovery,
} from './submission-recovery';
import { createSubmission, findSubmission, getSubmission } from './submissions-api';

export const labels = {
  AC: '通过',
  WA: '答案错误',
  PE: '格式错误',
  CE: '编译错误',
  RE: '运行错误',
  TLE: '超过时间限制',
  MLE: '超过内存限制',
  OLE: '超过输出限制',
  SE: '系统错误',
} satisfies Record<NonNullable<SubmissionData['verdict']>, string>;

export function SubmissionPanel({
  userId,
  problem,
  source,
  disabled,
  runControl,
  renderResults,
  onSubmissionStart,
}: {
  userId: string;
  problem: ProblemDetail;
  source: string;
  disabled: boolean;
  runControl?: ReactNode;
  renderResults?: (results: ReactNode) => ReactNode;
  onSubmissionStart?: () => void;
}) {
  const queryClient = useQueryClient();
  const navigate = useNavigate({ from: '/problems/$slug' });
  const search = useSearch({ from: '/_site/problems/$slug' });
  const storageKey = recoveryKey(userId, problem.problemId);
  const [initial] = useState(() => {
    try {
      return { recovery: loadRecovery(storageKey), error: '' };
    } catch {
      return {
        recovery: undefined,
        error: '无法读取本机提交恢复记录。请保留代码，恢复浏览器存储后重试。',
      };
    }
  });
  const [recovery, setRecovery] = useState(initial.recovery);
  const [notice, setNotice] = useState(initial.error);
  const locked = useRef(false);
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);
  const id = search.submissionId ?? recovery?.submissionId;
  const requestKey = recovery?.key;
  const submit = useMutation({
    mutationFn: (value: SubmissionRecovery) => createSubmission(value.key, value.body, userId),
    retry: false,
    onSuccess: (value, original) => {
      if (!alive.current) return;
      void queryClient.invalidateQueries({
        queryKey: ['submission-history', userId, problem.problemId],
      });
      try {
        save({ ...original, submissionId: value.id });
      } catch {
        setNotice('已受理。恢复记录未保存到本机，请保留当前页面链接。');
      }
      void navigate({
        search: (previous) => ({ ...previous, submissionId: value.id }),
        replace: true,
      });
    },
    onError: (error) => {
      if (!alive.current) return;
      if (
        (error instanceof ApiError && [400, 403, 404, 413, 422].includes(error.status ?? 0)) ||
        (error instanceof ApiError && error.code === 'PROBLEM_VERSION_CHANGED')
      ) {
        try {
          localStorage.removeItem(storageKey);
          setRecovery(undefined);
        } catch {
          /* Keep the original request available when storage is unavailable. */
        }
      }
      const detail = error instanceof Error ? error.message : '提交请求未完成。';
      setNotice(
        error instanceof ApiError && error.requestId
          ? `${detail} 查询编号：${error.requestId}`
          : detail,
      );
    },
    onSettled: () => {
      locked.current = false;
    },
  });
  const result = useQuery({
    queryKey: ['submission', userId, problem.problemId, id ?? requestKey],
    enabled: !disabled && !submit.isPending && Boolean(id || requestKey),
    queryFn: async ({ signal }) => {
      const value = id
        ? await getSubmission(id, signal)
        : await findSubmission(requestKey ?? '', signal);
      if (value.problemId !== problem.problemId)
        throw new Error('该提交不属于当前题目，请打开对应题目查看。');
      return value;
    },
    retry: false,
    gcTime: 0,
    refetchInterval: (query) => {
      if (query.state.data?.status === 'DONE') return false;
      if (
        query.state.error instanceof ApiError &&
        [401, 403, 404].includes(query.state.error.status ?? 0)
      )
        return false;
      return Math.min(15_000, 2000 * 2 ** Math.min(query.state.fetchFailureCount, 3));
    },
  });
  const shown = result.data;
  useEffect(() => {
    if (!shown || search.submissionId === shown.id) return;
    void navigate({
      search: (previous) => ({ ...previous, submissionId: shown.id }),
      replace: true,
    });
  }, [shown, search.submissionId, navigate]);
  function save(value: SubmissionRecovery) {
    // Persist the original payload before sending: retries must never read the edited source.
    localStorage.setItem(storageKey, JSON.stringify(value));
    setRecovery(value);
  }
  const unknown = Boolean(recovery && !shown && !recovery.submissionId);
  const busy = submit.isPending || (shown !== undefined && shown.status !== 'DONE');
  const invalidSource = !source.trim() || new TextEncoder().encode(source).length > 262144;
  function send(original?: SubmissionRecovery) {
    if (locked.current || disabled) return;
    locked.current = true;
    const next = original ?? {
      key: newSubmissionKey(),
      body: {
        problemId: problem.problemId,
        expectedProblemVersionId: problem.problemVersionId,
        languageId: 'cpp' as const,
        source,
      },
    };
    try {
      save(next);
    } catch {
      locked.current = false;
      setNotice('无法保存提交恢复记录，尚未发送。请恢复浏览器存储后重试。');
      return;
    }
    setNotice('');
    void navigate({
      search: (previous) => ({ ...previous, submissionId: undefined }),
      replace: true,
    });
    onSubmissionStart?.();
    submit.mutate(next);
  }
  const queryError =
    result.error instanceof ApiError && result.error.status === 404
      ? id
        ? '提交不存在或不属于当前账号。'
        : '尚未查到受理记录。原请求可能仍在处理，可以继续查询，或用原代码重试同一次提交。'
      : result.error instanceof Error
        ? result.error.message
        : null;
  const results = (
    <>
      <div
        id="submission-notice"
        className="text-fg-2 space-y-2 text-xs"
        role="status"
        aria-live="polite"
      >
        {disabled ? <p>登录状态暂时无法确认，提交与查询已暂停。</p> : null}
        {invalidSource ? <p>请输入非空 C++ 源码，最多 256 KiB。</p> : null}
        {notice ? <p className="break-words">{notice}</p> : null}
        {queryError && !submit.isPending ? <p className="break-words">{queryError}</p> : null}
        {!shown && (id || requestKey) && result.isFetching ? <p>正在确认提交结果…</p> : null}
        {shown ? <SubmissionResult value={shown} /> : null}
      </div>
      {(unknown || result.isError) && !disabled && !submit.isPending ? (
        <div className="flex flex-wrap gap-2">
          <Button
            size="sm"
            variant="secondary"
            disabled={result.isFetching || submit.isPending}
            onClick={() => void result.refetch()}
          >
            确认原请求结果
          </Button>
          {unknown &&
          result.error instanceof ApiError &&
          result.error.status === 404 &&
          recovery ? (
            <Button
              size="sm"
              variant="secondary"
              disabled={submit.isPending}
              onClick={() => send(recovery)}
            >
              用原代码重试同一次提交
            </Button>
          ) : null}
        </div>
      ) : null}
    </>
  );
  return (
    <div className="border-border-soft bg-panel max-h-1/2 shrink-0 space-y-3 overflow-y-auto border-t px-4 py-3">
      <div className="flex flex-wrap items-center justify-end gap-2">
        {runControl ?? (
          <Button size="sm" variant="secondary" disabled aria-describedby="run-unavailable">
            <Play aria-hidden="true" />
            运行
          </Button>
        )}
        <Button
          size="sm"
          disabled={disabled || busy || unknown || invalidSource || Boolean(initial.error)}
          onClick={() => send()}
          aria-describedby="submission-notice"
        >
          <Upload aria-hidden="true" />
          {submit.isPending ? '正在提交…' : '提交'}
        </Button>
      </div>
      {!runControl ? (
        <p id="run-unavailable" className="text-fg-muted text-xs">
          自定义运行暂未开放。
        </p>
      ) : null}
      {renderResults ? renderResults(results) : results}
    </div>
  );
}

export function SubmissionResult({ value }: { value: SubmissionData }) {
  return (
    <section aria-label="本次提交结果" className="space-y-2">
      <p className="text-foreground">
        {value.status === 'DONE' && value.verdict
          ? `${value.verdict} · ${labels[value.verdict]}`
          : value.status === 'JUDGING'
            ? '正在判题'
            : '等待判题'}
      </p>
      <p className="text-fg-meta font-mono break-all">
        {value.id} · v{value.problemVersionNo}
      </p>
      <div className="text-fg-2 flex flex-wrap gap-x-4 gap-y-1 font-mono">
        {value.passedCount !== undefined && value.totalCount !== undefined ? (
          <span>
            通过 {value.passedCount} / {value.totalCount}
          </span>
        ) : null}
        {value.executedCount !== undefined ? <span>已执行 {value.executedCount}</span> : null}
        {value.cpuNs !== undefined ? <span>CPU {value.cpuNs / 1_000_000} ms</span> : null}
        {value.memoryBytes !== undefined ? <span>内存 {value.memoryBytes / 1024} KiB</span> : null}
      </div>
      {value.message && (value.verdict === 'CE' || value.verdict === 'SE') ? (
        <pre className="max-h-40 overflow-auto font-mono text-xs break-words whitespace-pre-wrap">
          {value.message}
        </pre>
      ) : null}
      <p className="text-fg-muted">结果对应提交时的代码，继续编辑不会改变本次结果。</p>
    </section>
  );
}
