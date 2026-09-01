import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Badge } from './badge';

describe('Badge', () => {
  it('renders status meaning as readable text rather than color alone', () => {
    render(<Badge variant="success">成功 · 样例全部通过</Badge>);

    expect(screen.getByText('成功 · 样例全部通过')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('renders as any element through the Base UI render prop', () => {
    // 这是换成官方实现后拿到的能力：手写的 <span> 版本做不到，
    // 想让徽章可点击必须再包一层。
    render(
      <Badge
        variant="brand"
        // eslint-disable-next-line jsx-a11y/anchor-has-content -- Base UI render 模式下 children 由 Badge 注入，规则看不到
        render={(anchorProps) => <a href="/problems?tag=dp" {...anchorProps} />}
      >
        动态规划
      </Badge>,
    );

    const badge = screen.getByRole('link', { name: '动态规划' });
    expect(badge).toHaveAttribute('data-slot', 'badge');
    expect(badge).toHaveAttribute('href', '/problems?tag=dp');
  });
});
