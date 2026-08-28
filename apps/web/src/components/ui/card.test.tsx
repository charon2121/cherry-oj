import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { Card, CardDescription, CardHeader, CardTitle, Panel } from './card';
import { Link } from './link';

describe('Card and Panel', () => {
  it('keeps an interactive card as a container around a real link', async () => {
    const user = userEvent.setup();
    render(
      <Card variant="interactive">
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

  it('pairs selected styling with a visible selection label', () => {
    render(
      <Card selected selectionLabel="当前题目">
        <CardTitle>P1042</CardTitle>
      </Card>,
    );

    const card = screen.getByText('P1042').closest('[data-slot="card"]');
    expect(card).toHaveAttribute('data-selected');
    expect(card).toHaveClass('border-border-strong', 'bg-accent', 'text-foreground');
    expect(card).toHaveTextContent('当前题目P1042');
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
