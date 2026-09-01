import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { RotateCcw, Search } from 'lucide-react';
import { useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Panel } from '@/components/ui/card';
import { FormField } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Cluster, Container, Section, Stack } from '@/components/ui/layout';
import { SelectField } from '@/components/ui/select';
import { Heading, Text } from '@/components/ui/typography';
import type { ProblemSummary } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';

import { listProblems, problemKeys, type ProblemSearch } from '../api/problems-api';

type Props = {
  search: ProblemSearch;
  navigate: (search: ProblemSearch) => void;
};

export function ProblemListPage({ search, navigate }: Props) {
  const [keyword, setKeyword] = useState(search.q ?? '');
  const [tagText, setTagText] = useState(search.tag?.join(', ') ?? '');
  const result = useQuery({
    queryKey: problemKeys.publicList(search),
    queryFn: ({ signal }) => listProblems(search, signal),
    placeholderData: keepPreviousData,
  });
  const filtered = Boolean(
    search.q || search.difficulty || search.codeMode || search.language || search.tag?.length,
  );

  return (
    <Container>
      <Section className="py-10">
        <Stack gap={2}>
          <Text size="sm" tone="muted">
            公开题库
          </Text>
          <Heading level={1} size="2xl">
            选择下一道题
          </Heading>
          <Text size="sm" tone="muted">
            题面、样例和可用语言来自当前公开版本。
          </Text>
        </Stack>
        <form
          className="bg-surface border-border mt-6 grid gap-3 rounded-lg border p-4 md:grid-cols-2 xl:grid-cols-[minmax(12rem,1fr)_repeat(5,minmax(8rem,auto))_auto]"
          onSubmit={(event) => {
            event.preventDefault();
            navigate({
              ...search,
              q: keyword.trim() || undefined,
              tag: tagText
                .split(',')
                .map((tag) => tag.trim())
                .filter(Boolean)
                .slice(0, 10),
              cursor: undefined,
            });
          }}
        >
          <FormField label="关键词">
            <Input
              value={keyword}
              maxLength={100}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="标题或标识"
            />
          </FormField>
          <FormField label="标签">
            <Input
              value={tagText}
              maxLength={200}
              onChange={(event) => setTagText(event.target.value)}
              placeholder="数组, 哈希表"
            />
          </FormField>
          <SelectField
            label="难度"
            value={search.difficulty ?? ''}
            onValueChange={(value) =>
              navigate({
                ...search,
                difficulty: (value || undefined) as ProblemSearch['difficulty'],
                cursor: undefined,
              })
            }
            items={[
              { value: '', label: '全部难度' },
              { value: 'UNRATED', label: '未评级' },
              { value: 'EASY', label: '简单' },
              { value: 'MEDIUM', label: '中等' },
              { value: 'HARD', label: '困难' },
            ]}
          />
          <SelectField
            label="语言"
            value={search.language ?? ''}
            onValueChange={(value) =>
              navigate({ ...search, language: value || undefined, cursor: undefined })
            }
            items={[
              { value: '', label: '全部语言' },
              { value: 'cpp', label: 'C++' },
            ]}
          />
          <SelectField
            label="模式"
            value={search.codeMode ?? ''}
            onValueChange={(value) =>
              navigate({
                ...search,
                codeMode: (value || undefined) as ProblemSearch['codeMode'],
                cursor: undefined,
              })
            }
            items={[
              { value: '', label: '全部模式' },
              { value: 'ACM', label: 'ACM' },
              { value: 'CORE', label: '核心代码' },
            ]}
          />
          <SelectField
            label="排序"
            value={search.sort}
            onValueChange={(value) =>
              navigate({
                ...search,
                sort: value as ProblemSearch['sort'],
                cursor: undefined,
              })
            }
            items={[
              { value: 'UPDATED_DESC', label: '最近更新' },
              { value: 'UPDATED_ASC', label: '最早更新' },
              { value: 'TITLE_ASC', label: '标题' },
            ]}
          />
          <Button type="submit" className="self-end">
            <Search aria-hidden="true" />
            筛选
          </Button>
        </form>

        {result.isPending ? (
          <AsyncState
            className="mt-6"
            variant="loading"
            size="page"
            title="正在加载题库…"
            progressLabel="正在加载题库…"
          >
            {null}
          </AsyncState>
        ) : null}
        {result.isError && !result.data ? (
          <AsyncState
            className="mt-6"
            variant="error"
            size="page"
            live="assertive"
            title={isInvalidCursor(result.error) ? '题库游标已失效' : '题库暂时无法加载'}
            action={
              isInvalidCursor(result.error) ? (
                <Button
                  variant="secondary"
                  onClick={() => navigate({ ...search, cursor: undefined })}
                >
                  返回首批
                </Button>
              ) : (
                <Button variant="secondary" onClick={() => void result.refetch()}>
                  <RotateCcw aria-hidden="true" />
                  重试
                </Button>
              )
            }
          >
            {isInvalidCursor(result.error)
              ? '列表已发生变化，请从首批继续浏览。'
              : problemError(result.error)}
          </AsyncState>
        ) : null}
        {result.data?.items.length === 0 ? (
          <AsyncState
            className="mt-6"
            variant="empty"
            size="page"
            title={filtered ? '没有符合筛选的题目' : '题库还没有公开题目'}
            action={
              filtered ? (
                <Button
                  variant="secondary"
                  onClick={() => {
                    setKeyword('');
                    setTagText('');
                    navigate({ sort: 'UPDATED_DESC', size: search.size });
                  }}
                >
                  清除筛选
                </Button>
              ) : undefined
            }
          >
            换一组条件试试。
          </AsyncState>
        ) : null}
        {result.data && result.data.items.length > 0 ? (
          <div className="mt-6 grid gap-2" aria-busy={result.isFetching || undefined}>
            {result.isError ? (
              <Panel className="mb-2" role="alert">
                <Text>下一批加载失败，当前结果已保留。</Text>
                <Button className="mt-3" variant="secondary" onClick={() => void result.refetch()}>
                  重试当前批次
                </Button>
              </Panel>
            ) : null}
            {result.data.items.map((problem) => (
              <ProblemRow key={problem.problemId} problem={problem} />
            ))}
            <nav
              aria-label="题库分页"
              className="mt-3 flex flex-wrap items-center justify-between gap-3"
            >
              <Text size="sm" tone="muted">
                本批 {result.data.items.length} 道题{result.isFetching ? '，正在更新…' : ''}
              </Text>
              <Cluster gap={2}>
                {search.cursor ? (
                  <Button
                    variant="secondary"
                    onClick={() => navigate({ ...search, cursor: undefined })}
                  >
                    返回首批
                  </Button>
                ) : null}
                <Button
                  variant="secondary"
                  disabled={!result.data.pagination.hasMore || result.isFetching}
                  onClick={() =>
                    navigate({ ...search, cursor: result.data?.pagination.nextCursor ?? undefined })
                  }
                >
                  下一批
                </Button>
              </Cluster>
            </nav>
          </div>
        ) : null}
      </Section>
    </Container>
  );
}

function ProblemRow({ problem }: { problem: ProblemSummary }) {
  return (
    <Panel className="hover:bg-accent p-0 transition-colors">
      <Link
        to="/problems/$slug"
        params={{ slug: problem.slug }}
        search={{}}
        className="focus-visible:outline-ring grid min-w-0 gap-3 rounded-lg p-4 no-underline focus-visible:outline-2 focus-visible:outline-offset-2 sm:grid-cols-[1fr_auto]"
      >
        <Stack gap={2}>
          <Cluster gap={2}>
            <Heading level={2} size="lg">
              {problem.title}
            </Heading>
            <Badge>{difficultyLabel(problem.difficulty)}</Badge>
            <Badge>{problem.codeMode}</Badge>
          </Cluster>
          <Text size="sm" tone="muted" className="font-mono">
            {problem.slug} · v{problem.versionNo}
          </Text>
          <Cluster gap={2}>
            {problem.tags.map((tag) => (
              <Badge key={tag} variant="info">
                {tag}
              </Badge>
            ))}
          </Cluster>
        </Stack>
        <Text size="sm" tone="secondary" className="self-center">
          {problem.allowedLanguages.map((language) => language.displayName).join(' / ')}
        </Text>
      </Link>
    </Panel>
  );
}

function difficultyLabel(value: string) {
  return (
    ({ UNRATED: '未评级', EASY: '简单', MEDIUM: '中等', HARD: '困难' } as Record<string, string>)[
      value
    ] ?? value
  );
}
function problemError(error: unknown) {
  return error instanceof ApiError && error.kind === 'contract'
    ? '题库服务返回了无效数据，请稍后重试。'
    : '网络或题库服务异常，已有内容不会被清空。';
}

function isInvalidCursor(error: unknown) {
  return error instanceof ApiError && error.code === 'INVALID_CURSOR';
}
