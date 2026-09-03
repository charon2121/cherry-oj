import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { createColumnHelper, tableFeatures, useTable } from '@tanstack/react-table';
import { RotateCcw } from 'lucide-react';
import { useMemo } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Panel } from '@/components/ui/card';
import { FormField } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Cluster, Container, Section } from '@/components/ui/layout';
import { SelectField } from '@/components/ui/select';
import { Heading, Text } from '@/components/ui/typography';
import type { AdminProblem } from '@/generated/api';

import { createProblem, listAdminProblems, problemKeys } from '../api/problems-api';
import { AdminProblemCreateDialog } from './admin-problem-create-dialog';

export type AdminProblemSearch = { page: number; q: string; status: 'ALL' | 'ACTIVE' | 'ARCHIVED' };

const features = tableFeatures({});
const columnHelper = createColumnHelper<typeof features, AdminProblem>();
const columns = columnHelper.columns([
  columnHelper.accessor('slug', { header: '标识' }),
  columnHelper.display({
    id: 'title',
    header: '当前版本',
    cell: ({ row }) => {
      const latest = row.original.versions[0];
      return latest ? `${latest.title} · v${latest.versionNo}` : '—';
    },
  }),
  columnHelper.accessor('visibility', { header: '可见性' }),
  columnHelper.accessor('status', {
    header: '状态',
    cell: ({ getValue }) => (
      <Badge variant={getValue() === 'ACTIVE' ? 'success' : 'neutral'}>{getValue()}</Badge>
    ),
  }),
  columnHelper.display({
    id: 'action',
    header: '操作',
    cell: ({ row }) => {
      const version = row.original.versions[0];
      return version ? (
        <Link
          className={buttonVariants({ size: 'sm', variant: 'secondary' })}
          to="/admin/problems/$problemId/versions/$versionId"
          params={{ problemId: row.original.id, versionId: version.id }}
        >
          打开工作台
        </Link>
      ) : (
        '—'
      );
    },
  }),
]);

export function AdminProblemListPage({
  navigate,
  search,
}: {
  navigate: (search: AdminProblemSearch) => void;
  search: AdminProblemSearch;
}) {
  const queryClient = useQueryClient();
  const routeNavigate = useNavigate();
  const problems = useQuery({
    queryKey: problemKeys.adminList(search.q, search.status, search.page),
    queryFn: ({ signal }) => listAdminProblems(search.q, search.status, search.page, signal),
    placeholderData: keepPreviousData,
  });
  const create = useMutation({
    mutationFn: createProblem,
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: problemKeys.admin });
      const version = created.versions[0];
      if (version) {
        await routeNavigate({
          to: '/admin/problems/$problemId/versions/$versionId',
          params: { problemId: created.id, versionId: version.id },
        });
      }
    },
  });
  const data = useMemo(() => problems.data?.items ?? [], [problems.data?.items]);
  const table = useTable({ features, columns, data });

  return (
    <Container>
      <Section>
        <Heading level={1} className="sr-only">
          题目管理
        </Heading>
        <Panel className="p-[var(--ds-space-4)]">
          <form
            className="grid gap-[var(--ds-space-3)] sm:grid-cols-[minmax(12rem,1fr)_10rem_auto_auto]"
            onSubmit={(event) => {
              event.preventDefault();
              const formData = new FormData(event.currentTarget);
              const queryValue = formData.get('q');
              navigate({
                ...search,
                page: 1,
                q: typeof queryValue === 'string' ? queryValue.trim() : '',
              });
            }}
          >
            <FormField label="搜索题目">
              <Input name="q" defaultValue={search.q} />
            </FormField>
            <SelectField
              label="状态"
              value={search.status}
              onValueChange={(value) =>
                navigate({ ...search, page: 1, status: value as AdminProblemSearch['status'] })
              }
              items={[
                { value: 'ALL', label: '全部' },
                { value: 'ACTIVE', label: '进行中' },
                { value: 'ARCHIVED', label: '已归档' },
              ]}
            />
            <Button className="self-end" variant="secondary" type="submit">
              查询
            </Button>
            <div className="self-end sm:justify-self-end">
              <AdminProblemCreateDialog
                creating={create.isPending}
                onCreate={async (value) => {
                  await create.mutateAsync({ ...value, codeMode: 'ACM', languageId: 'cpp' });
                }}
              />
            </div>
          </form>
        </Panel>

        <Panel className="mt-[var(--ds-space-4)] overflow-x-auto p-0">
          {problems.isPending ? (
            <div className="p-[var(--ds-space-6)]">
              <AsyncState
                variant="loading"
                size="inline"
                title="正在加载题目…"
                progressLabel="正在加载题目…"
              >
                {null}
              </AsyncState>
            </div>
          ) : null}
          {problems.isError ? (
            <div className="p-[var(--ds-space-6)]">
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
            </div>
          ) : null}
          {problems.data ? (
            <table className="w-full min-w-4xl text-left text-[length:var(--ds-text-sm)]">
              <thead className="bg-[var(--ds-surface-translucent)] text-[var(--ds-fg-meta)]">
                {table.getHeaderGroups().map((group) => (
                  <tr key={group.id}>
                    {group.headers.map((header) => (
                      <th
                        key={header.id}
                        className="px-[var(--ds-space-4)] py-[var(--ds-space-3)] font-[var(--ds-weight-body)]"
                      >
                        {header.isPlaceholder ? null : <table.FlexRender header={header} />}
                      </th>
                    ))}
                  </tr>
                ))}
              </thead>
              <tbody className="divide-y divide-[var(--ds-border-soft)]">
                {table.getRowModel().rows.map((row) => (
                  <tr
                    key={row.id}
                    className="transition-colors duration-[var(--ds-motion-fast)] hover:bg-[var(--ds-surface-translucent-hover)] motion-reduce:transition-none"
                  >
                    {row.getAllCells().map((cell) => (
                      <td key={cell.id} className="px-[var(--ds-space-4)] py-[var(--ds-space-3)]">
                        <table.FlexRender cell={cell} />
                      </td>
                    ))}
                  </tr>
                ))}
                {data.length === 0 ? (
                  <tr>
                    <td
                      className="px-[var(--ds-space-4)] py-[var(--ds-space-8)] text-center"
                      colSpan={5}
                    >
                      没有符合条件的题目。
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          ) : null}
        </Panel>
        {problems.data ? (
          <nav aria-label="题目管理分页" className="mt-[var(--ds-space-4)]">
            <Cluster justify="between" gap={3}>
              <Text size="sm" tone="muted">
                第 {problems.data.pagination.page} /{' '}
                {Math.max(1, problems.data.pagination.totalPages)} 页
              </Text>
              <Cluster gap={2}>
                <Button
                  variant="secondary"
                  disabled={search.page <= 1}
                  onClick={() => navigate({ ...search, page: search.page - 1 })}
                >
                  上一页
                </Button>
                <Button
                  variant="secondary"
                  disabled={search.page >= problems.data.pagination.totalPages}
                  onClick={() => navigate({ ...search, page: search.page + 1 })}
                >
                  下一页
                </Button>
              </Cluster>
            </Cluster>
          </nav>
        ) : null}
      </Section>
    </Container>
  );
}
