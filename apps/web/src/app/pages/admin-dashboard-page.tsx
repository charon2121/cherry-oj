import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { BookOpen, KeyRound, RotateCcw, Server, TriangleAlert } from 'lucide-react';
import type { ReactNode } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  DataList,
  type DataListItem,
  dataListRowLinkClasses,
  DataListStatusDot,
} from '@/components/ui/data-list';
import { Stack } from '@/components/ui/layout';
import { ListPageTemplate } from '@/components/ui/page-templates';
import { Toolbar } from '@/components/ui/toolbar';
import { Text } from '@/components/ui/typography';
import { judgeNodeKeys, listJudgeNodes } from '@/features/admin-judging/api/judge-nodes-api';
import { adminUserKeys, listUsers } from '@/features/admin-users/api/admin-users-api';
import { listAdminProblems, problemKeys } from '@/features/problems/api/problems-api';
import { apiErrorMessage } from '@/lib/api/api-error-message';
import { cn } from '@/lib/utils';

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
        {row.link === 'users' ? null : <span aria-hidden="true">{row.statusLabel}</span>}
      </>
    ),
    meta:
      row.link === 'users' ? undefined : (
        <span aria-hidden="true">{row.compactStatusLabel ?? row.statusLabel}</span>
      ),
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
      <Text size="sm" tone="meta" className="px-1 py-2">
        {emptyLabel}
      </Text>
    ) : null))
  );
  // 有历史结果时优先展示故障和恢复入口，避免较长的节点列表把它们推到屏幕外。
  const message = feedback ? <div>{feedback}</div> : null;

  return (
    <section aria-label={label} className="flex min-w-0 flex-col gap-2.5">
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

function KpiSummaryCard({
  icon: Icon,
  label,
  tone = 'neutral',
  value,
  meta,
}: {
  icon: typeof Server;
  label: string;
  tone?: 'success' | 'warning' | 'neutral';
  value: string;
  meta: string;
}) {
  return (
    <Card padding="sm" className="min-w-0 p-3 sm:p-4">
      <div className="flex min-w-0 items-center justify-between gap-2">
        <span className="text-fg-muted truncate text-xs font-medium">{label}</span>
        <span
          className={cn(
            'hidden size-7 shrink-0 items-center justify-center rounded-lg border sm:flex',
            tone === 'success' && 'border-success-border bg-success-soft text-success',
            tone === 'warning' && 'border-warning-border bg-warning-soft text-warning',
            tone === 'neutral' && 'border-border bg-surface-subtle text-fg-meta',
          )}
        >
          <Icon aria-hidden="true" className="size-3.5" />
        </span>
      </div>
      <div className="mt-1.5 flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
        <span className="text-foreground font-heading text-xl tabular-nums sm:text-2xl">
          {value}
        </span>
        <span className="text-fg-meta hidden truncate text-xs sm:inline">{meta}</span>
      </div>
    </Card>
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
      toolbar={<Toolbar title="总览" className="md:hidden" />}
    >
      <Stack gap={4}>
        <div aria-label="平台概览指标" className="grid grid-cols-3 gap-2 sm:gap-4">
          <KpiSummaryCard
            icon={Server}
            label="在线节点"
            tone={judgeNodes.data ? (onlineNodes > 0 ? 'success' : 'warning') : 'neutral'}
            value={judgeNodes.data ? String(onlineNodes) : '—'}
            meta={judgeNodes.data ? `共 ${nodes.length} 台注册` : '读取中'}
          />
          <KpiSummaryCard
            icon={BookOpen}
            label="私有草稿"
            tone="neutral"
            value={problems.data ? String(drafts.length) : '—'}
            meta={problemsArePaginated ? '当前页待公开' : '待完善并公开'}
          />
          <KpiSummaryCard
            icon={KeyRound}
            label="待首次改密"
            tone={users.data && pendingUsers.length > 0 ? 'warning' : 'neutral'}
            value={users.data ? String(pendingUsers.length) : '—'}
            meta={usersArePaginated ? '当前页初始账号' : '活跃初始账号'}
          />
        </div>
        <DashboardSection
          label="判题节点"
          query={judgeNodes}
          rows={nodes}
          count={`${onlineNodes} / ${nodes.length} 在线`}
          notice={
            onlineNodes === 0 ? (
              <div className="flex items-start gap-2 px-1" role="status">
                <TriangleAlert
                  className="text-warning mt-half size-4 shrink-0"
                  aria-hidden="true"
                />
                <Text size="sm" className="text-warning">
                  没有在线判题节点
                </Text>
              </div>
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
