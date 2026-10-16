import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { RotateCcw } from 'lucide-react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import {
  DataList,
  type DataListGroup,
  dataListRowLinkClasses,
  DataListStatusDot,
} from '@/components/ui/data-list';
import { Heading, Text } from '@/components/ui/typography';
import { judgeNodeKeys, listJudgeNodes } from '@/features/admin-judging/api/judge-nodes-api';
import { adminUserKeys, listUsers } from '@/features/admin-users/api/admin-users-api';
import { listAdminProblems, problemKeys } from '@/features/problems/api/problems-api';

type TodoRow = Readonly<{
  id: string;
  title: string;
  detail: string;
  status: 'done' | 'partial' | 'none';
  statusLabel: string;
  link: 'problem' | 'users' | 'none';
}>;

// 总览是管理员进来先看的地方，回答"现在有什么要我处理"（linear.app 的 Inbox / My issues）。
// 只列需要动手的事；没有待办时说清楚，不用跳转卡片填满首屏。
const lastSeenFormat = new Intl.DateTimeFormat('zh-CN', {
  month: 'numeric',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

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
      statusLabel: problem.hasTestData ? '有测试数据，待校准与公开' : '还没有测试数据',
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
    link: 'none',
  }));
  const onlineNodes = nodes.filter((node) => node.status === 'done').length;

  // 判题节点组总是显示：没有在线节点时，所有提交都会停在"暂时无法判题"，这是首要待办。
  const groups: DataListGroup<TodoRow>[] = [
    { key: 'drafts', label: '私有题目，尚未公开', count: drafts.length, rows: drafts },
    { key: 'users', label: '账号待首次改密', count: pendingUsers.length, rows: pendingUsers },
  ].filter((group) => group.rows.length > 0);
  if (judgeNodes.data !== undefined) {
    groups.push({
      key: 'nodes',
      label: onlineNodes === 0 ? '判题节点 · 没有在线节点，提交无法判题' : '判题节点',
      count: `${onlineNodes} / ${nodes.length} 在线`,
      rows: nodes,
    });
  }

  const loading = problems.isPending || users.isPending || judgeNodes.isPending;
  const failed = problems.isError || users.isError || judgeNodes.isError;

  let body;
  if (loading) {
    body = (
      <AsyncState
        variant="loading"
        size="inline"
        title="正在汇总待办…"
        progressLabel="正在汇总待办…"
      >
        {null}
      </AsyncState>
    );
  } else if (failed) {
    body = (
      <AsyncState
        variant="error"
        size="inline"
        title="待办加载失败"
        action={
          <Button
            variant="secondary"
            onClick={() => {
              void problems.refetch();
              void users.refetch();
              void judgeNodes.refetch();
            }}
          >
            <RotateCcw aria-hidden="true" />
            重试
          </Button>
        }
      >
        题目、账号或判题节点状态暂时取不到。
      </AsyncState>
    );
  } else if (groups.length === 0) {
    body = (
      <AsyncState variant="empty" size="inline" title="没有待办">
        题目都已公开，账号都已完成首次改密。
      </AsyncState>
    );
  }

  return (
    <div className="min-w-0 pt-6 pb-12">
      <div className="px-gutter-phone sm:px-gutter-tablet lg:px-gutter-desktop mx-2">
        <Heading level={1} className="font-body text-2xl">
          总览
        </Heading>
        {problems.data && users.data ? (
          <Text size="sm" tone="meta" className="mt-1">
            {problems.data.pagination.totalElements} 道在用题目 ·{' '}
            {users.data.pagination.totalElements} 个账号
          </Text>
        ) : null}
      </div>
      <div className="mt-6">
        {body === undefined ? (
          <DataList
            caption="待办"
            groups={groups}
            rowKey={(row) => `${row.link}-${row.id}`}
            rowInteractive
            renderRow={(row) => ({
              leading: <DataListStatusDot status={row.status} label={row.statusLabel} />,
              id: row.detail,
              title:
                row.link === 'problem' ? (
                  <Link
                    to="/admin/problems/$problemId"
                    params={{ problemId: row.id }}
                    className={dataListRowLinkClasses}
                  >
                    {row.title}
                  </Link>
                ) : row.link === 'users' ? (
                  <Link to="/admin/users" search={{ page: 1 }} className={dataListRowLinkClasses}>
                    {row.title}
                  </Link>
                ) : (
                  row.title
                ),
              trailing: <span>{row.statusLabel}</span>,
              meta: <span>{row.statusLabel}</span>,
            })}
          />
        ) : (
          <div className="px-gutter-phone sm:px-gutter-tablet lg:px-gutter-desktop mx-2">
            {body}
          </div>
        )}
      </div>
    </div>
  );
}

export { AdminDashboardPage };
