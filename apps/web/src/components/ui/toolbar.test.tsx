import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { Toolbar, ToolbarFilterGroup } from './toolbar';

describe('Toolbar', () => {
  it('applies a filter immediately instead of behind a submit button', async () => {
    // 构图合同：过滤即时生效。出现"筛选"提交按钮说明退回了后台表单的做法。
    const onValueChange = vi.fn();
    render(
      <Toolbar
        title="题库"
        filters={
          <ToolbarFilterGroup
            label="完成状态"
            value="all"
            onValueChange={onValueChange}
            options={[
              { value: 'all', label: '全部' },
              { value: 'done', label: '已通过' },
            ]}
          />
        }
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: '已通过' }));

    expect(onValueChange).toHaveBeenCalledWith('done');
    expect(screen.queryByRole('button', { name: /筛选|提交|查询/ })).not.toBeInTheDocument();
  });

  it('exposes the selected filter through aria-pressed rather than colour alone', () => {
    render(
      <Toolbar
        title="题库"
        filters={
          <ToolbarFilterGroup
            label="完成状态"
            value="done"
            onValueChange={() => undefined}
            options={[
              { value: 'all', label: '全部' },
              { value: 'done', label: '已通过' },
            ]}
          />
        }
      />,
    );

    expect(screen.getByRole('button', { name: '已通过' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '全部' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('group', { name: '完成状态' })).toBeInTheDocument();
  });

  it('shows an exact count next to the title', () => {
    render(<Toolbar title="题库" count="1,284 道题" />);

    expect(screen.getByText('题库')).toBeInTheDocument();
    expect(screen.getByText('1,284 道题')).toBeInTheDocument();
  });
});
