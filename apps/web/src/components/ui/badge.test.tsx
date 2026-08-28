import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Badge } from './badge';

describe('Badge', () => {
  it('renders status meaning as readable text rather than color alone', () => {
    render(<Badge variant="success">成功 · 样例全部通过</Badge>);

    expect(screen.getByText('成功 · 样例全部通过')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('adds a visible structural cue for selection', () => {
    render(
      <Badge variant="brand" selected>
        当前筛选
      </Badge>,
    );

    const badge = screen.getByText('当前筛选').closest('[data-slot="badge"]');
    expect(badge).toHaveTextContent('已选择·当前筛选');
    expect(badge).toHaveAttribute('data-selected');
  });
});
