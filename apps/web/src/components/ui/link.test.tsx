import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { Link } from './link';

describe('Link', () => {
  it('renders a real, underlined and keyboard-focusable link', async () => {
    const user = userEvent.setup();
    render(<Link href="#details">查看提交详情</Link>);

    const link = screen.getByRole('link', { name: '查看提交详情' });
    expect(link).toHaveAttribute('href', '#details');
    expect(link).toHaveClass('underline');

    await user.tab();
    expect(link).toHaveFocus();
  });

  it('announces and safely opens an external destination', () => {
    render(
      <Link href="https://example.test/docs" external size="standalone">
        外部文档
      </Link>,
    );

    const link = screen.getByRole('link', { name: '外部文档（在新窗口打开）' });
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noreferrer');
  });

  it('derives the new-window announcement from the resolved target', () => {
    const { rerender } = render(
      <Link href="https://example.test/manual" target="_blank">
        手动新窗口
      </Link>,
    );

    expect(screen.getByRole('link', { name: '手动新窗口（在新窗口打开）' })).toHaveAttribute(
      'rel',
      'noreferrer',
    );

    rerender(
      <Link href="https://example.test/same-window" external target="_self">
        当前窗口
      </Link>,
    );
    const sameWindowLink = screen.getByRole('link', { name: '当前窗口' });
    expect(sameWindowLink).toHaveAttribute('target', '_self');
    expect(sameWindowLink).not.toHaveTextContent('在新窗口打开');
  });
});
