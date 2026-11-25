import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { RotateCcw } from 'lucide-react';
import type { ReactNode } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import {
  DataList,
  type DataListItem,
  dataListRowLinkClasses,
  DataListStatusDot,
} from '@/components/ui/data-list';
import { InlineNotice } from '@/components/ui/inline-notice';
import { Stack } from '@/components/ui/layout';
import { ListPageTemplate } from '@/components/ui/page-templates';
import { Toolbar } from '@/components/ui/toolbar';
import { Text } from '@/components/ui/typography';
import { judgeNodeKeys, listJudgeNodes } from '@/features/admin-judging/api/judge-nodes-api';
import { adminUserKeys, listUsers } from '@/features/admin-users/api/admin-users-api';
import { listAdminProblems, problemKeys } from '@/features/problems/api/problems-api';
import { apiErrorMessage } from '@/lib/api/api-error-message';

type TodoRow = Readonly<{
  id: string;
  title: string;
  detail: string;
  status: 'done' | 'partial' | 'none';
  statusLabel: string;
  compactStatusLabel?: string;
  link: 'problem' | 'users' | 'none';
}>;

const lastSeenFormat = new Intl.DateTimeFormat('zh-CN', {
  month: 'numeric',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

function renderTodoRow(row: TodoRow): DataListItem {
  let title: ReactNode = row.title;
  if (row.link === 'problem') {
    title = (
      <Link
        to="/admin/problems/$problemId"
        params={{ problemId: row.id }}
        className={dataListRowLinkClasses}
      >
        {row.title}
      </Link>
    );
  } else if (row.link === 'users') {
    title = (
      <Link to="/admin/users" search={{ page: 1 }} className={dataListRowLinkClasses}>
        {row.title}
      </Link>
    );
  }

  return {
    leading: <DataListStatusDot status={row.status} label={row.statusLabel} />,
    title: <span className="block wrap-anywhere whitespace-normal">{title}</span>,
    trailing: (
      <>
        <span className="w-28 truncate" title={row.detail}>
          {row.detail}
        </span>
        <span aria-hidden="true">{row.statusLabel}</span>
      </>
    ),
    meta: <span aria-hidden="true">{row.compactStatusLabel ?? row.statusLabel}</span>,
  };
}

type DashboardSectionProps = Readonly<{
  label: string;
  rows: readonly TodoRow[];
  query: Pick<
    UseQueryResult,
    'data' | 'error' | 'isError' | 'isFetching' | 'isPending' | 'refetch'
  >;
  count: string;
  emptyLabel?: string;
  notice?: ReactNode;
}>;

function DashboardSection({
  count,
  emptyLabel,
  label,
  notice,
  query,
  rows,
}: DashboardSectionProps) {
  const hasData = query.data !== undefined;
  const loadingLabel = `正在加载${label}…`;
  const feedback = query.isPending ? (
    <AsyncState variant="loading" size="inline" title={loadingLabel} progressLabel={loadingLabel}>
      {null}
    </AsyncState>
  ) : query.isError ? (
    <AsyncState
      variant="error"
      size="inline"
      title={`${label}加载失败`}
      live="polite"
      retrying={query.isFetching}
      action={
        <Button
          variant="secondary"
          size="sm"
          disabled={query.isFetching}
          onClick={() => void query.refetch()}
        >
          <RotateCcw aria-hidden="true" />
          重试{label}
        </Button>
      }
    >
      {apiErrorMessage(query.error, '请稍后重试。')}
    </AsyncState>
  ) : (
    (notice ??
    (rows.length === 0 && emptyLabel ? (
      <Text size="sm" tone="meta" className="py-3">
        {emptyLabel}
      </Text>
    ) : null))
  );
  // 有历史结果时优先展示故障和恢复入口，避免较长的节点列表把它们推到屏幕外。
  const message = feedback ? (
    <div className="px-gutter-phone sm:px-gutter-tablet lg:px-gutter-desktop mx-2">{feedback}</div>
  ) : null;

  return (
    <section aria-label={label} className="flex min-w-0 flex-col gap-2">
      {rows.length > 0 ? message : null}
      <DataList
        caption={`${label}列表`}
        align="packed"
        groups={[
          {
            key: label,
            label,
            count: hasData ? (query.isError ? `上次 · ${count}` : count) : undefined,
            rows,
          },
        ]}
        rowKey={(row) => row.id}
        rowInteractive
        renderRow={renderTodoRow}
      />
      {rows.length === 0 ? message : null}
    </section>
  );
}

function AdminDashboardPage() {
  const problems = useQuery({
    queryKey: problemKeys.adminList('', 'ACTIVE', 1),
    queryFn: ({ signal }) => listAdminProblems('', 'ACTIVE', 1, signal),
  });
  const users = useQuery({
    queryKey: adminUserKeys.list(1, 20),
    queryFn: ({ signal }) => listUsers(1, 20, signal),
  });
  const judgeNodes = useQuery({
    queryKey: judgeNodeKeys.all,
    queryFn: ({ signal }) => listJudgeNodes(signal),
    refetchInterval: 30_000,
  });

  const drafts: TodoRow[] = (problems.data?.items ?? [])
    .filter((problem) => problem.visibility === 'PRIVATE')
    .map((problem) => ({
      id: problem.id,
      title: problem.title,
      detail: problem.slug,
      status: problem.hasTestData ? 'partial' : 'none',
      statusLabel: problem.hasTestData ? '测试数据已上传' : '还没有测试数据',
      link: 'problem',
    }));
  const pendingUsers: TodoRow[] = (users.data?.items ?? [])
    .filter((user) => user.status === 'ACTIVE' && user.passwordChangeRequired)
    .map((user) => ({
      id: user.id,
      title: user.username,
      detail: user.role === 'ADMIN' ? '管理员' : '用户',
      status: 'none',
      statusLabel: '还没有完成首次登录改密',
      link: 'users',
    }));

  const nodes: TodoRow[] = (judgeNodes.data ?? []).map((node) => ({
    id: node.nodeId,
    title: node.nodeId,
    detail: node.languages.join(' / '),
    status: node.online ? 'done' : 'none',
    statusLabel: node.online
      ? '在线'
      : `离线 · 最近心跳 ${lastSeenFormat.format(new Date(node.lastSeenAt))}`,
    compactStatusLabel: node.online ? '在线' : '离线',
    link: 'none',
  }));
  const onlineNodes = nodes.filter((node) => node.status === 'done').length;
  const problemsArePaginated = (problems.data?.pagination.totalPages ?? 0) > 1;
  const usersArePaginated = (users.data?.pagination.totalPages ?? 0) > 1;

  return (
    <ListPageTemplate
      title="总览"
      width="column"
      toolbar={<Toolbar title="总览" className="mx-1 sm:mx-2 md:hidden" />}
    >
      <Stack gap={4} className="mt-3 md:mt-0">
        <DashboardSection
          label="判题节点"
          query={judgeNodes}
          rows={nodes}
          count={`${onlineNodes} / ${nodes.length} 在线`}
          notice={
            onlineNodes === 0 ? (
              <InlineNotice variant="warning" live="polite" title="没有在线判题节点">
                提交暂时无法判题。
              </InlineNotice>
            ) : undefined
          }
        />
        <DashboardSection
          label="私有题目"
          query={problems}
          rows={drafts}
          count={`${problemsArePaginated ? '本页 ' : ''}${drafts.length} 道`}
          emptyLabel={problemsArePaginated ? '本页没有私有题目' : '没有私有题目'}
        />
        <DashboardSection
          label="待首次改密的账号"
          query={users}
          rows={pendingUsers}
          count={`${usersArePaginated ? '本页 ' : ''}${pendingUsers.length} 个`}
          emptyLabel={usersArePaginated ? '本页没有待首次改密的账号' : '没有待首次改密的账号'}
        />
      </Stack>
    </ListPageTemplate>
  );
}

export { AdminDashboardPage };
