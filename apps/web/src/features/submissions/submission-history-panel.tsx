import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { useEffect } from 'react';

import { Button, buttonVariants } from '@/components/ui/button';
import { DataList } from '@/components/ui/data-list';
import { SelectField } from '@/components/ui/select';
import { TextEditor } from '@/components/ui/text-editor';
import { Toolbar } from '@/components/ui/toolbar';
import { sessionQueryOptions } from '@/features/auth/api/session-query';
import { SourceCopyButton } from '@/features/problems/components/source-copy-button';
import type { ProblemDetail, SubmissionSourceData } from '@/generated/api';

import {
  getHistorySource,
  historyKeys,
  type HistorySearch,
  isPrivateAccessError,
  listProblemSubmissions,
  verdictSchema,
  withHistoryIdentity,
} from './submission-history-api';
import { labels, SubmissionResult } from './submission-panel';
import { getSubmission } from './submissions-api';

export type HistoryLoad = { userId: string; source: SubmissionSourceData; versionNo: number };

type Props = { problem: ProblemDetail; canLoad: boolean; onLoad: (value: HistoryLoad) => void };

export function SubmissionHistoryPanel(props: Props) {
  const session = useQuery(sessionQueryOptions());
  if (session.isPending) return <p className="text-fg-muted p-4">正在确认登录状态…</p>;
  if (session.isError)
    return (
      <div className="space-y-3 p-4">
        <p>登录状态暂时无法确认。</p>
        <Button variant="secondary" onClick={() => void session.refetch()}>
          重试
        </Button>
      </div>
    );
  const user = session.data?.authenticated ? session.data.user : undefined;
  if (!user)
    return (
      <div className="space-y-3 p-4">
        <p className="text-fg-2">登录后查看自己在本题的提交记录。</p>
        <Link
          to="/login"
          search={{ returnTo: `/problems/${props.problem.slug}?tab=submissions` }}
          className={buttonVariants({ size: 'sm' })}
        >
          登录
        </Link>
      </div>
    );
  if (user.status !== 'ACTIVE')
    return <p className="text-fg-muted p-4">当前账号不可用，请重新登录。</p>;
  if (user.passwordChangeRequired)
    return (
      <div className="p-4">
        <Link to="/account/password" className={buttonVariants({ size: 'sm' })}>
          修改密码后查看提交记录
        </Link>
      </div>
    );
  return <OwnedHistory key={`${user.id}:${props.problem.problemId}`} {...props} userId={user.id} />;
}

function OwnedHistory({ userId, ...props }: Props & { userId: string }) {
  const client = useQueryClient();
  const search = useSearch({ from: '/_site/problems/$slug' });
  const navigate = useNavigate({ from: '/problems/$slug' });
  const change = (patch: Partial<HistorySearch>) =>
    void navigate({ search: (previous) => ({ ...previous, ...patch }), replace: true });
  const problemId = props.problem.problemId;
  useEffect(
    () => () => {
      const queryKey = historyKeys.all(userId, problemId);
      void client.cancelQueries({ queryKey });
      client.removeQueries({ queryKey });
    },
    [client, userId, problemId],
  );
  const list = useQuery({
    queryKey: [
      ...historyKeys.all(userId, problemId),
      'list',
      search.historyPage ?? 1,
      search.historyVerdict,
    ],
    queryFn: ({ signal }) =>
      withHistoryIdentity(client, userId, () =>
        listProblemSubmissions(userId, problemId, search, signal),
      ),
    enabled: !search.historySubmissionId,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  });
  if (search.historySubmissionId)
    return (
      <HistoryDetail
        key={search.historySubmissionId}
        {...props}
        userId={userId}
        id={search.historySubmissionId}
        onBack={() => change({ historySubmissionId: undefined })}
      />
    );
  const shown = isPrivateAccessError(list.error) ? undefined : list.data;
  return (
    <div className="min-w-0">
      <Toolbar
        title="本题提交"
        count={shown ? `${shown.pagination.totalElements} 条` : undefined}
        actions={
          <Button
            size="sm"
            variant="ghost"
            disabled={list.isFetching}
            onClick={() => void list.refetch()}
          >
            刷新
          </Button>
        }
        filters={
          <SelectField
            label="判定"
            labelPlacement="hidden"
            className="relative"
            value={search.historyVerdict ?? 'ALL'}
            items={[
              { value: 'ALL', label: '全部判定' },
              ...verdictSchema.options.map((value) => ({
                value,
                label: `${value} · ${labels[value]}`,
              })),
            ]}
            onValueChange={(value) =>
              change({
                historyVerdict: value === 'ALL' ? undefined : verdictSchema.parse(value),
                historyPage: 1,
              })
            }
          />
        }
      />
      {list.isPending ? (
        <p className="text-fg-muted p-4" role="status">
          正在加载提交记录…
        </p>
      ) : null}
      {list.isError ? (
        <p className="text-fg-2 p-4" role="alert">
          {list.error.message} 可点击刷新重试。
        </p>
      ) : null}
      {shown?.items.length === 0 ? (
        <div className="space-y-2 p-4">
          <p className="text-fg-muted">
            {shown.pagination.totalElements > 0
              ? '本页没有记录，请返回第一页。'
              : search.historyVerdict
                ? '没有符合条件的提交。'
                : '本题还没有提交记录。'}
          </p>
          {shown.pagination.page > 1 ? (
            <Button size="sm" variant="secondary" onClick={() => change({ historyPage: 1 })}>
              返回第一页
            </Button>
          ) : null}
          {search.historyVerdict ? (
            <Button
              size="sm"
              variant="secondary"
              onClick={() => change({ historyVerdict: undefined, historyPage: 1 })}
            >
              清除筛选
            </Button>
          ) : null}
        </div>
      ) : null}
      {shown ? (
        <>
          <DataList
            caption="本题提交记录"
            align="packed"
            rows={shown.items}
            rowKey={(row) => row.id}
            renderRow={(row) => ({
              title: (
                <button
                  type="button"
                  className="focus-visible:outline-ring block w-full py-2 text-left outline-none focus-visible:outline-1 focus-visible:forced-colors:outline-solid"
                  onClick={() => change({ historySubmissionId: row.id })}
                >
                  <span className="text-foreground block">
                    {row.verdict
                      ? `${row.verdict} · ${labels[row.verdict]}`
                      : row.status === 'JUDGING'
                        ? '正在判题'
                        : '等待判题'}
                  </span>
                  <span className="text-fg-meta block font-mono text-xs whitespace-normal">
                    {row.id.slice(-8)} · C++ · {new Date(row.createdAt).toLocaleString()}
                  </span>
                </button>
              ),
            })}
          />
          <div className="text-fg-muted flex flex-wrap items-center gap-3 p-4 text-xs">
            <Button
              size="sm"
              variant="secondary"
              disabled={shown.pagination.page <= 1}
              onClick={() => change({ historyPage: shown.pagination.page - 1 })}
            >
              上一页
            </Button>
            <span>
              第 {shown.pagination.page} 页 · 共 {shown.pagination.totalPages} 页
            </span>
            <Button
              size="sm"
              variant="secondary"
              disabled={shown.pagination.page >= shown.pagination.totalPages}
              onClick={() => change({ historyPage: shown.pagination.page + 1 })}
            >
              下一页
            </Button>
          </div>
        </>
      ) : null}
    </div>
  );
}

function HistoryDetail({
  userId,
  id,
  onBack,
  problem,
  canLoad,
  onLoad,
}: Props & { userId: string; id: string; onBack: () => void }) {
  const client = useQueryClient();
  const result = useQuery({
    queryKey: [...historyKeys.all(userId, problem.problemId), 'result', id],
    queryFn: ({ signal }) =>
      withHistoryIdentity(client, userId, async () => {
        const value = await getSubmission(id, signal);
        if (value.problemId !== problem.problemId) throw new Error('该提交不属于当前题目。');
        return value;
      }),
    gcTime: 0,
    retry: false,
    refetchInterval: (query) =>
      query.state.data?.status === 'DONE' || query.state.error ? false : 2000,
  });
  const source = useQuery({
    queryKey: [...historyKeys.all(userId, problem.problemId), 'source', id],
    queryFn: ({ signal }) =>
      withHistoryIdentity(client, userId, () =>
        getHistorySource(userId, problem.problemId, id, signal),
      ),
    gcTime: 0,
    staleTime: Infinity,
    retry: false,
  });
  const denied = isPrivateAccessError(source.error) || isPrivateAccessError(result.error);
  const code = denied ? undefined : source.data;
  return (
    <section aria-label="历史提交详情" className="min-w-0 space-y-4 p-4">
      <Button size="sm" variant="ghost" onClick={onBack}>
        返回提交记录
      </Button>
      {result.isPending ? (
        <p className="text-fg-muted" role="status">
          正在读取结果…
        </p>
      ) : null}
      {result.data && !denied ? (
        <>
          <p className="text-foreground break-words">{result.data.problemTitle}</p>
          <p className="text-fg-meta text-xs">
            {new Date(result.data.createdAt).toLocaleString()} · C++
          </p>
          <SubmissionResult value={result.data} />
        </>
      ) : null}
      {result.isError ? (
        <div className="space-y-2">
          <p role="alert">{result.error.message}</p>
          <Button size="sm" variant="secondary" onClick={() => void result.refetch()}>
            重试结果
          </Button>
        </div>
      ) : null}
      {source.isPending ? (
        <p className="text-fg-muted" role="status">
          正在读取历史代码…
        </p>
      ) : null}
      {source.isError ? (
        <div className="space-y-2">
          <p role="alert">{source.error.message}</p>
          <Button size="sm" variant="secondary" onClick={() => void source.refetch()}>
            重试代码
          </Button>
        </div>
      ) : null}
      {code ? (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <SourceCopyButton value={code.source} label="复制历史代码" />
            <Button
              size="sm"
              variant="secondary"
              disabled={!canLoad || !result.data || result.isError}
              onClick={() => {
                if (result.data)
                  onLoad({ userId, source: code, versionNo: result.data.problemVersionNo });
              }}
            >
              载入编辑器
            </Button>
          </div>
          {!canLoad ? (
            <p className="text-fg-muted text-xs">
              当前题目或登录状态尚不可编辑；若题目已更新，请先打开新版本。
            </p>
          ) : null}
          <TextEditor
            value={code.source}
            onChange={() => undefined}
            readOnly
            language="cpp"
            size="code"
            aria-label="历史提交代码"
          />
          <p className="text-fg-muted text-xs">这是提交时的原始代码。查看记录不会改变右侧草稿。</p>
        </>
      ) : null}
    </section>
  );
}
