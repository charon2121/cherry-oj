import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { DataList, type DataListColumn, dataListRowLinkClasses } from './data-list';

type Row = { id: string; slug: string; title: string; rate: string };

const rows: Row[] = [
  { id: '1', slug: 'CO-1042', title: '线段树区间和', rate: '48%' },
  { id: '2', slug: 'CO-1041', title: '背包', rate: '51%' },
];

const columns: DataListColumn<Row>[] = [
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
      <a href={`/p/${row.id}`} className={dataListRowLinkClasses}>
        {row.title}
      </a>
    ),
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

describe('DataList', () => {
  it('renders a real table with an accessible name so the list is navigable', () => {
    render(<DataList caption="题库" columns={columns} rows={rows} rowKey={(row) => row.id} />);

    const table = screen.getByRole('table', { name: '题库' });
    expect(table).toBeInTheDocument();
    expect(within(table).getAllByRole('row')).toHaveLength(rows.length + 1);
  });

  it('shares one declared width per column so columns line up across rows', () => {
    // 列对齐是构图合同里能在截图上判定的一条：同一列的宽度由列定义给出，不随内容浮动。
    render(<DataList caption="题库" columns={columns} rows={rows} rowKey={(row) => row.id} />);

    // jsdom 把 rem 归一化成 px（8rem = 128px）；断言的是"同一列宽度一致且来自列定义"，
    // 不是字面量写法。按行取第 1 个单元格，避开窄屏折行时同一内容的第二份渲染。
    const [header] = screen.getAllByRole('columnheader', { name: '标识' });
    expect(header).toHaveStyle({ width: '128px' });
    for (const row of screen.getAllByRole('row').slice(1)) {
      expect(within(row).getAllByRole('cell')[0]).toHaveStyle({ width: '128px' });
    }
  });

  it('makes a clickable row exactly one link instead of a div with onClick', () => {
    render(
      <DataList
        caption="题库"
        columns={columns}
        rows={rows}
        rowKey={(row) => row.id}
        rowInteractive
      />,
    );

    // 每个数据行恰好一个链接：不是 div+onClick，也不是标题与整行两个可点区域。
    for (const row of screen.getAllByRole('row').slice(1)) {
      expect(within(row).getAllByRole('link')).toHaveLength(1);
    }
    expect(screen.getByRole('link', { name: '线段树区间和' })).toHaveAttribute('href', '/p/1');
  });

  it('conveys row status with a shape and readable text, not colour alone', () => {
    render(
      <DataList
        caption="题库"
        columns={columns}
        rows={rows}
        rowKey={(row) => row.id}
        rowStatus={(row) => (row.id === '1' ? 'done' : 'none')}
        rowStatusLabel={(row) => (row.id === '1' ? '已通过' : '未开始')}
      />,
    );

    expect(screen.getByText('已通过')).toBeInTheDocument();
    expect(screen.getByText('未开始')).toBeInTheDocument();
  });

  it('keeps secondary columns in the row instead of truncating them on narrow screens', () => {
    // 窄屏的降级方式是折行，不是裁切也不是横向滚动：次要列的内容仍然在 DOM 里。
    render(<DataList caption="题库" columns={columns} rows={rows} rowKey={(row) => row.id} />);

    const bodyRows = screen.getAllByRole('row').slice(1);
    for (const row of bodyRows) {
      // 次要列在窄屏折进主列，因此同一内容在 DOM 中出现两次：一次在自己的列，
      // 一次在折行区。两份都在，说明降级方式是折行而不是裁切。
      expect(within(row).getAllByText(/^CO-10/).length).toBe(2);
    }
  });
});
