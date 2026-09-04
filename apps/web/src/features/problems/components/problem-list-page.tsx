import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { RotateCcw } from 'lucide-react';
import { useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { DataList, dataListRowLinkClasses } from '@/components/ui/data-list';
import { ListPageTemplate } from '@/components/ui/page-templates';
import { SearchInput } from '@/components/ui/search-input';
import { SelectField } from '@/components/ui/select';
import { Toolbar } from '@/components/ui/toolbar';
import { Text } from '@/components/ui/typography';
import type { ProblemSummary } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';

import { listProblems, problemKeys, type ProblemSearch } from '../api/problems-api';
import { DifficultyIcon, difficultyLabel } from './difficulty-icon';

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

  const applyText = () => {
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
  };

  const clearFilters = () => {
    setKeyword('');
    setTagText('');
    navigate({ sort: 'UPDATED_DESC', size: search.size });
  };

  // 一行两簇：左簇是难度图标 + 标识 + 标题（标题吃掉剩余宽度），右簇是标签与语言。
  // 模式（ACM/CORE）和版本号从列表里拿掉——它们是筛选维度和技术细节，不是扫视时要看的东西。
  const renderRow = (problem: ProblemSummary) => ({
    leading: <DifficultyIcon difficulty={problem.difficulty} />,
    id: problem.slug,
    title: (
      <Link
        to="/problems/$slug"
        params={{ slug: problem.slug }}
        search={{}}
        className={dataListRowLinkClasses}
      >
        {problem.title}
      </Link>
    ),
    trailing: (
      <>
        {problem.tags.slice(0, 2).map((tag) => (
          <Badge key={tag}>{tag}</Badge>
        ))}
        <span>{problem.allowedLanguages.map((language) => language.displayName).join(' / ')}</span>
      </>
    ),
    meta: (
      <>
        <span>{difficultyLabel(problem.difficulty)}</span>
        {problem.tags.slice(0, 1).map((tag) => (
          <span key={tag}>{tag}</span>
        ))}
      </>
    ),
  });

  // 工具条第一行放标题、计数与关键词搜索；第二行放其余筛选。除关键词与标签需要
  // 敲完再提交（回车即生效，没有"筛选"按钮）外，下拉一律选中即生效。
  const toolbar = (
    <Toolbar
      title="题库"
      count={
        result.data === undefined
          ? undefined
          : `本批 ${result.data.items.length} 道题${result.isFetching ? '，正在更新…' : ''}`
      }
      search={
        <form
          className="flex min-w-0 items-center gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            applyText();
          }}
        >
          <SearchInput
            aria-label="关键词"
            value={keyword}
            maxLength={100}
            placeholder="标题或标识"
            containerClassName="w-56"
            onChange={(event) => setKeyword(event.target.value)}
          />
          <SearchInput
            aria-label="标签"
            value={tagText}
            maxLength={200}
            placeholder="数组, 哈希表"
            containerClassName="w-48"
            onChange={(event) => setTagText(event.target.value)}
          />
          {/* 表单需要一个提交按钮才能响应回车；它对屏幕阅读器可见，视觉上不占位。 */}
          <button type="submit" className="sr-only">
            应用关键词与标签筛选
          </button>
        </form>
      }
      filters={
        <>
          <SelectField
            labelPlacement="hidden"
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
            labelPlacement="hidden"
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
            labelPlacement="hidden"
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
            labelPlacement="hidden"
            label="排序"
            value={search.sort}
            onValueChange={(value) =>
              navigate({ ...search, sort: value as ProblemSearch['sort'], cursor: undefined })
            }
            items={[
              { value: 'UPDATED_DESC', label: '最近更新' },
              { value: 'UPDATED_ASC', label: '最早更新' },
              { value: 'TITLE_ASC', label: '标题' },
            ]}
          />
        </>
      }
    >
      {filtered ? (
        <Button variant="toolbar" size="sm" onClick={clearFilters}>
          清除筛选
        </Button>
      ) : null}
    </Toolbar>
  );

  const pageState = result.isPending ? (
    <AsyncState variant="loading" size="page" title="正在加载题库…" progressLabel="正在加载题库…">
      {null}
    </AsyncState>
  ) : result.isError && !result.data ? (
    <AsyncState
      variant="error"
      size="page"
      live="assertive"
      title={isInvalidCursor(result.error) ? '题库游标已失效' : '题库暂时无法加载'}
      action={
        isInvalidCursor(result.error) ? (
          <Button variant="secondary" onClick={() => navigate({ ...search, cursor: undefined })}>
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
  ) : result.data?.items.length === 0 ? (
    <AsyncState
      variant="empty"
      size="page"
      title={filtered ? '没有符合筛选的题目' : '题库还没有公开题目'}
      action={
        filtered ? (
          <Button variant="secondary" onClick={clearFilters}>
            清除筛选
          </Button>
        ) : undefined
      }
    >
      换一组条件试试。
    </AsyncState>
  ) : undefined;

  return (
    <ListPageTemplate
      title="题库"
      toolbar={toolbar}
      state={pageState}
      pagination={
        result.data === undefined ? undefined : (
          <nav aria-label="题库分页" className="flex flex-wrap items-center justify-between gap-3">
            <Text size="sm" tone="muted">
              {result.data.pagination.hasMore ? '还有更多题目' : '已到最后一批'}
            </Text>
            <span className="flex items-center gap-2">
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
            </span>
          </nav>
        )
      }
    >
      {result.isError && result.data ? (
        <div
          role="alert"
          className="border-danger-border bg-danger-soft mb-4 rounded-lg border p-4"
        >
          <Text size="sm">下一批加载失败，当前结果已保留。</Text>
          <Button className="mt-3" variant="secondary" onClick={() => void result.refetch()}>
            重试当前批次
          </Button>
        </div>
      ) : null}
      {result.data === undefined ? null : (
        <DataList
          caption="题库"
          rows={result.data.items}
          rowKey={(problem) => problem.problemId}
          renderRow={renderRow}
          rowInteractive
          aria-busy={result.isFetching || undefined}
        />
      )}
    </ListPageTemplate>
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
