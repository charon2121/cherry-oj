import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { Badge } from './badge';
import { DataList, dataListRowLinkClasses, DataListStatusDot } from './data-list';

type Problem = {
  id: string;
  slug: string;
  title: string;
  tags: string[];
  bars: number;
  state: 'done' | 'partial' | 'none';
};

const rows: Problem[] = [
  {
    id: '1',
    slug: 'segment-tree-range-sum',
    title: '线段树区间和',
    tags: ['数据结构'],
    bars: 2,
    state: 'done',
  },
  {
    id: '2',
    slug: 'knapsack-duplicates',
    title: '带重复的背包',
    tags: ['dp'],
    bars: 2,
    state: 'partial',
  },
  {
    id: '3',
    slug: 'grid-dijkstra',
    title: '网格上的 Dijkstra',
    tags: ['图论'],
    bars: 3,
    state: 'done',
  },
  {
    id: '4',
    slug: 'two-sum-sorted',
    title: '两数之和（有序输入）',
    tags: ['双指针'],
    bars: 1,
    state: 'none',
  },
  {
    id: '5',
    slug: 'min-spanning-cactus',
    title: '最小生成仙人掌，一道标题相当长的题目用来检验标题是否吃掉剩余宽度',
    tags: ['mst'],
    bars: 3,
    state: 'none',
  },
];

function Bars({ count }: { count: number }) {
  return (
    <span className="inline-flex items-end gap-px">
      {[0, 1, 2].map((index) => (
        <span
          key={index}
          className={`w-[3px] rounded-[1px] ${index === 0 ? 'h-[5px]' : index === 1 ? 'h-[8px]' : 'h-[11px]'} ${
            index < count ? 'bg-fg-muted' : 'bg-border-solid'
          }`}
        />
      ))}
    </span>
  );
}

const meta = {
  title: 'UI/DataList',
  parameters: { layout: 'fullscreen' },
  tags: ['autodocs'],
} satisfies Meta<typeof DataList>;

export default meta;
type Story = StoryObj<typeof meta>;

const renderRow = (row: Problem) => ({
  leading: <Bars count={row.bars} />,
  id: row.slug,
  title: (
    <a href={`/problems/${row.slug}`} className={dataListRowLinkClasses}>
      {row.title}
    </a>
  ),
  trailing: (
    <>
      {row.tags.map((tag) => (
        <Badge key={tag}>{tag}</Badge>
      ))}
      <span>C++</span>
    </>
  ),
});

export const Default: Story = {
  render: () => (
    <DataList
      caption="题库"
      rows={rows}
      rowKey={(row) => row.id}
      renderRow={renderRow}
      rowInteractive
    />
  ),
};

export const Grouped: Story = {
  render: () => (
    <DataList
      caption="题库"
      rowKey={(row) => row.id}
      renderRow={renderRow}
      rowInteractive
      groups={[
        {
          key: 'todo',
          label: '未开始',
          count: 2,
          icon: <DataListStatusDot status="none" label="未开始" />,
          rows: rows.filter((row) => row.state === 'none'),
        },
        {
          key: 'done',
          label: '已通过',
          count: 2,
          icon: <DataListStatusDot status="done" label="已通过" />,
          rows: rows.filter((row) => row.state === 'done'),
        },
      ]}
    />
  ),
};

export const Empty: Story = {
  render: () => (
    <DataList caption="题库" rows={[]} rowKey={(row) => row.id} renderRow={renderRow} />
  ),
};

export const Narrow: Story = {
  parameters: { viewport: { defaultViewport: 'mobile1' } },
  render: () => (
    <DataList
      caption="题库"
      rows={rows}
      rowKey={(row) => row.id}
      rowInteractive
      renderRow={(row) => ({ ...renderRow(row), meta: <span>{row.tags[0]}</span> })}
    />
  ),
};
