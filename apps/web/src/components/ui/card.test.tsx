import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { Card, CardAction, CardDescription, CardHeader, CardTitle, Panel } from './card';
import { Link } from './link';

describe('Card and Panel', () => {
  it('keeps an interactive card as a container around a real link', async () => {
    const user = userEvent.setup();
    render(
      <Card>
        <CardHeader>
          <CardTitle>
            <Link href="#problem">P1042 · 字符统计</Link>
          </CardTitle>
          <CardDescription>统计一行文本中每个字符出现的次数。</CardDescription>
        </CardHeader>
      </Card>,
    );

    const link = screen.getByRole('link', { name: 'P1042 · 字符统计' });
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    await user.tab();
    expect(link).toHaveFocus();
  });

  it('lays out CardAction beside the title through the official header grid', () => {
    // CardAction 是换成官方实现后新增的子组件：标题区变成两列网格，
    // 操作区固定落在右上角，不需要每个调用处自己拼 flex。
    render(
      <Card>
        <CardHeader>
          <CardTitle>P1042</CardTitle>
          <CardAction>
            <button type="button">收藏</button>
          </CardAction>
        </CardHeader>
      </Card>,
    );

    const header = screen.getByText('P1042').closest('[data-slot="card-header"]');
    expect(header).toHaveClass('has-data-[slot=card-action]:grid-cols-[minmax(0,1fr)_auto]');
    expect(
      screen.getByRole('button', { name: '收藏' }).closest('[data-slot="card-action"]'),
    ).toHaveClass('col-start-2');
  });

  it('renders a named panel as a semantic region', () => {
    render(
      <Panel aria-labelledby="panel-title">
        <CardTitle id="panel-title">运行结果</CardTitle>
      </Panel>,
    );

    expect(screen.getByRole('region', { name: '运行结果' })).toBeInTheDocument();
  });
});
