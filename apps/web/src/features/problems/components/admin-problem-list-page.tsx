import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { RotateCcw } from 'lucide-react';
import { useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import { DataList, type DataListGroup, dataListRowLinkClasses } from '@/components/ui/data-list';
import { Cluster } from '@/components/ui/layout';
import { ListPageTemplate } from '@/components/ui/page-templates';
import { SearchInput } from '@/components/ui/search-input';
import { Toolbar, ToolbarFilterGroup } from '@/components/ui/toolbar';
import { Text } from '@/components/ui/typography';
import type { AdminProblemSummary } from '@/generated/api';

import { createProblem, listAdminProblems, problemKeys } from '../api/problems-api';
import { AdminProblemCreateDialog } from './admin-problem-create-dialog';
import { DifficultyIcon } from './difficulty-icon';

export type AdminProblemSearch = { page: number; q: string; status: 'ALL' | 'ACTIVE' | 'ARCHIVED' };

const updatedAtFormat = new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' });

// 按可见性分组：私有题目就是草稿，管理员进来先看还有哪些没公开。组的顺序固定，空组不渲染。
function groupByVisibility(items: readonly AdminProblemSummary[]) {
  const groups: DataListGroup<AdminProblemSummary>[] = [];
  const drafts = items.filter((item) => item.visibility === 'PRIVATE');
  const published = items.filter((item) => item.visibility === 'PUBLIC');
  if (drafts.length > 0)
    groups.push({ key: 'private', label: '私有', count: drafts.length, rows: drafts });
  if (published.length > 0) {
    groups.push({ key: 'public', label: '已公开', count: published.length, rows: published });
  }
  return groups;
}

export function AdminProblemListPage({
  navigate,
  search,
}: {
  navigate: (search: AdminProblemSearch) => void;
  search: AdminProblemSearch;
}) {
  const queryClient = useQueryClient();
  const routeNavigate = useNavigate();
  const [keyword, setKeyword] = useState(search.q);
  const problems = useQuery({
    queryKey: problemKeys.adminList(search.q, search.status, search.page),
    queryFn: ({ signal }) => listAdminProblems(search.q, search.status, search.page, signal),
    placeholderData: keepPreviousData,
  });
  const create = useMutation({
    mutationFn: createProblem,
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: problemKeys.admin });
      await routeNavigate({ to: '/admin/problems/$problemId', params: { problemId: created.id } });
    },
  });

  // 一行：左边视图切换即时生效，右边搜索（回车生效）与新建。页面名称在外壳面包屑里，不再重复。
  const toolbar = (
    <Toolbar
      filters={
        <ToolbarFilterGroup
          label="题目状态"
          value={search.status}
          onValueChange={(status) => navigate({ ...search, page: 1, status })}
          options={[
            { value: 'ALL', label: '全部' },
            { value: 'ACTIVE', label: '在用' },
            { value: 'ARCHIVED', label: '已归档' },
          ]}
        />
      }
      search={
        <form
          className="flex min-w-0 items-center"
          onSubmit={(event) => {
            event.preventDefault();
            navigate({ ...search, page: 1, q: keyword.trim() });
          }}
        >
          <SearchInput
            aria-label="搜索题目"
            value={keyword}
            maxLength={100}
            placeholder="标题或标识"
            containerClassName="w-56"
            onChange={(event) => setKeyword(event.target.value)}
          />
          <button type="submit" className="sr-only">
            搜索
          </button>
        </form>
      }
      actions={
        <AdminProblemCreateDialog
          creating={create.isPending}
          onCreate={async (value) => {
            await create.mutateAsync({ ...value, languageId: 'cpp' });
          }}
        />
      }
    />
  );

  const state = problems.isPending ? (
    <AsyncState variant="loading" size="inline" title="正在加载题目…" progressLabel="正在加载题目…">
      {null}
    </AsyncState>
  ) : problems.isError ? (
    <AsyncState
      variant="error"
      size="inline"
      title="题目列表加载失败"
      action={
        <Button variant="secondary" onClick={() => void problems.refetch()}>
          <RotateCcw aria-hidden="true" />
          重试
        </Button>
      }
    >
      已有筛选条件会保留。
    </AsyncState>
  ) : problems.data.items.length === 0 ? (
    <AsyncState variant="empty" size="inline" title="没有符合条件的题目">
      {search.q || search.status !== 'ALL' ? '换一组条件试试。' : '用右上角的「新建题目」开始。'}
    </AsyncState>
  ) : undefined;

  const pagination =
    problems.data && problems.data.pagination.totalPages > 1 ? (
      <nav aria-label="题目管理分页">
        <Cluster justify="between" gap={3}>
          <Text size="sm" tone="muted">
            第 {problems.data.pagination.page} / {problems.data.pagination.totalPages} 页
          </Text>
          <Cluster gap={2}>
            <Button
              variant="secondary"
              size="sm"
              disabled={search.page <= 1}
              onClick={() => navigate({ ...search, page: search.page - 1 })}
            >
              上一页
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={search.page >= problems.data.pagination.totalPages}
              onClick={() => navigate({ ...search, page: search.page + 1 })}
            >
              下一页
            </Button>
          </Cluster>
        </Cluster>
      </nav>
    ) : undefined;

  return (
    <ListPageTemplate title="题目管理" toolbar={toolbar} state={state} pagination={pagination}>
      <DataList
        caption="题目"
        groups={groupByVisibility(problems.data?.items ?? [])}
        rowKey={(problem) => problem.id}
        rowInteractive
        aria-busy={problems.isFetching}
        renderRow={(problem) => ({
          leading: <DifficultyIcon difficulty={problem.difficulty} />,
          id: problem.slug,
          title: (
            <Link
              to="/admin/problems/$problemId"
              params={{ problemId: problem.id }}
              className={dataListRowLinkClasses}
            >
              {problem.title}
            </Link>
          ),
          trailing: (
            <>
              {problem.status === 'ARCHIVED' ? <span>已归档</span> : null}
              <span>{problem.hasTestData ? '有测试数据' : '没有测试数据'}</span>
              <time dateTime={problem.updatedAt} className="w-12 text-right tabular-nums">
                {updatedAtFormat.format(new Date(problem.updatedAt))}
              </time>
            </>
          ),
          meta: <span>{problem.hasTestData ? '有测试数据' : '没有测试数据'}</span>,
        })}
      />
    </ListPageTemplate>
  );
}
