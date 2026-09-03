import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { Badge } from './badge';
import { DataList, type DataListColumn, dataListRowLinkClasses } from './data-list';

type Problem = {
  id: string;
  slug: string;
  title: string;
  tags: string[];
  difficulty: '简单' | '中等' | '困难';
  rate: string;
  state: 'done' | 'partial' | 'none';
};

const rows: Problem[] = [
  {
    id: '1',
    slug: 'CO-1042',
    title: '线段树区间和',
    tags: ['数据结构'],
    difficulty: '中等',
    rate: '48%',
    state: 'done',
  },
  {
    id: '2',
    slug: 'CO-1041',
    title: '带重复的背包',
    tags: ['dp'],
    difficulty: '中等',
    rate: '51%',
    state: 'partial',
  },
  {
    id: '3',
    slug: 'CO-1039',
    title: '网格上的 Dijkstra',
    tags: ['图论'],
    difficulty: '困难',
    rate: '23%',
    state: 'done',
  },
  {
    id: '4',
    slug: 'CO-1036',
    title: '两数之和（有序输入）',
    tags: ['双指针'],
    difficulty: '简单',
    rate: '82%',
    state: 'none',
  },
  {
    id: '5',
    slug: 'CO-1030',
    title: '最小生成仙人掌，一道标题相当长的题目用来检验列宽是否稳定',
    tags: ['mst'],
    difficulty: '困难',
    rate: '11%',
    state: 'none',
  },
];

const difficultyTone: Record<Problem['difficulty'], string> = {
  简单: 'text-success',
  中等: 'text-warning',
  困难: 'text-danger',
};

const columns: DataListColumn<Problem>[] = [
  {
    id: 'slug',
    header: '标识',
    width: '8rem',
    mono: true,
    priority: 'secondary',
    cell: (row) => row.slug,
  },
  {
    id: 'title',
    header: '题目',
    cell: (row) => (
      <a href={`/problems/${row.slug}`} className={dataListRowLinkClasses}>
        {row.title}
      </a>
    ),
  },
  {
    id: 'tags',
    header: '标签',
    width: '10rem',
    priority: 'secondary',
    cell: (row) => row.tags.map((tag) => <Badge key={tag}>{tag}</Badge>),
  },
  {
    id: 'difficulty',
    header: '难度',
    width: '5rem',
    cell: (row) => <span className={difficultyTone[row.difficulty]}>{row.difficulty}</span>,
  },
  {
    id: 'rate',
    header: '通过率',
    width: '5rem',
    align: 'end',
    mono: true,
    priority: 'secondary',
    cell: (row) => row.rate,
  },
];

// DataList 是泛型组件：走 args 会让 Storybook 把 Row 推成 unknown（列定义随之失去类型）。
// 这里用 render-only 的写法保留类型参数。
const meta = {
  title: 'UI/DataList',
  parameters: { layout: 'padded' },
  tags: ['autodocs'],
} satisfies Meta<typeof DataList>;

export default meta;
type Story = StoryObj<typeof meta>;

const statusLabel: Record<Problem['state'], string> = {
  done: '已通过',
  partial: '尝试过',
  none: '未开始',
};

export const Default: Story = {
  render: () => (
    <DataList
      caption="题库"
      columns={columns}
      rows={rows}
      rowKey={(row) => row.id}
      rowInteractive
      rowStatus={(row) => row.state}
      rowStatusLabel={(row) => statusLabel[row.state]}
    />
  ),
};

export const VisibleCaption: Story = {
  render: () => (
    <DataList
      caption="题库"
      captionVisible
      columns={columns}
      rows={rows}
      rowKey={(row) => row.id}
    />
  ),
};

export const WithoutStatus: Story = {
  render: () => <DataList caption="题库" columns={columns} rows={rows} rowKey={(row) => row.id} />,
};

export const Empty: Story = {
  render: () => <DataList caption="题库" columns={columns} rows={[]} rowKey={(row) => row.id} />,
};

export const Narrow: Story = {
  parameters: { viewport: { defaultViewport: 'mobile1' } },
  render: () => (
    <DataList
      caption="题库"
      columns={columns}
      rows={rows}
      rowKey={(row) => row.id}
      rowInteractive
      rowStatus={(row) => row.state}
      rowStatusLabel={(row) => statusLabel[row.state]}
    />
  ),
};
