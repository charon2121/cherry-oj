import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { DataList, dataListRowLinkClasses } from './data-list';

type Row = { id: string; slug: string; title: string };

const rows: Row[] = [
  { id: '1', slug: 'segment-tree', title: '线段树区间和' },
  { id: '2', slug: 'knapsack', title: '背包' },
];

const renderRow = (row: Row) => ({
  leading: <span data-testid="leading" />,
  id: row.slug,
  title: (
    <a href={`/p/${row.id}`} className={dataListRowLinkClasses}>
      {row.title}
    </a>
  ),
  trailing: <span>C++</span>,
});

describe('DataList', () => {
  it('renders an accessible list rather than a data table', () => {
    // 参照的列表是列表，不是表格。表格会把剩余宽度均分给各列，六个字段摊满整行——
    // 那正是"去掉表头也去不掉表格感"的原因。
    render(<DataList caption="题库" rows={rows} rowKey={(row) => row.id} renderRow={renderRow} />);

    const list = screen.getByRole('list');
    expect(within(list).getAllByRole('listitem')).toHaveLength(rows.length);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: '题库' })).toBeInTheDocument();
  });

  it('gives the title the brightest tone and the identifier the weakest', () => {
    // 层级由颜色承担：参照把标识与标题设成同样的字号字重，只靠颜色分层。
    render(<DataList caption="题库" rows={rows} rowKey={(row) => row.id} renderRow={renderRow} />);

    expect(screen.getByRole('link', { name: '线段树区间和' })).toHaveClass('text-foreground');
    expect(screen.getByText('segment-tree')).toHaveClass('text-fg-meta');
  });

  it('lets the title take the remaining width instead of sharing it with columns', () => {
    render(<DataList caption="题库" rows={rows} rowKey={(row) => row.id} renderRow={renderRow} />);

    const titleSlot = screen.getByRole('link', { name: '线段树区间和' }).parentElement;
    expect(titleSlot).toHaveClass('flex-1');
  });

  it('makes a clickable row exactly one link instead of a div with onClick', () => {
    render(
      <DataList
        caption="题库"
        rows={rows}
        rowKey={(row) => row.id}
        renderRow={renderRow}
        rowInteractive
      />,
    );

    for (const row of screen.getAllByRole('listitem')) {
      expect(within(row).getAllByRole('link')).toHaveLength(1);
    }
  });

  it('renders a band per group so sections are separated by luminance, not by headings', () => {
    render(
      <DataList
        caption="题库"
        rowKey={(row) => row.id}
        renderRow={renderRow}
        groups={[{ key: 'todo', label: '未开始', count: 1, rows: [rows[0]!] }]}
      />,
    );

    expect(screen.getByText('未开始')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
  });
});
