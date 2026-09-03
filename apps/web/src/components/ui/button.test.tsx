import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { Button } from './button';

function TestButton() {
  const [count, setCount] = useState(0);

  return (
    <Button type="button" onClick={() => setCount((value) => value + 1)}>
      已点击 {count} 次
    </Button>
  );
}

describe('Button', () => {
  it('responds to pointer and keyboard activation', async () => {
    const user = userEvent.setup();
    render(<TestButton />);

    await user.click(screen.getByRole('button', { name: '已点击 0 次' }));
    await user.keyboard('{Enter}');

    expect(screen.getByRole('button', { name: '已点击 2 次' })).toBeInTheDocument();
  });

  it('keeps its label footprint while exposing a readable loading state', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <Button loading loadingLabel="正在提交代码" onClick={onClick}>
        提交代码
      </Button>,
    );

    const button = screen.getByRole('button', { name: '正在提交代码' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-label', '正在提交代码');
    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByRole('status')).toHaveTextContent('正在提交代码');
    expect(screen.getByText('提交代码')).toHaveClass('invisible');

    await user.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('does not activate a disabled danger action', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <Button variant="danger" disabled onClick={onClick}>
        删除提交
      </Button>,
    );

    await user.click(screen.getByRole('button', { name: '删除提交' }));
    expect(onClick).not.toHaveBeenCalled();
  });

  it('keeps disabled colors authoritative when the button is also pressed', () => {
    render(
      <Button variant="primary" disabled aria-pressed="true">
        已选择但不可用
      </Button>,
    );

    expect(screen.getByRole('button')).toHaveClass(
      'disabled:border-[var(--ds-border)]!',
      'disabled:bg-[var(--ds-surface-translucent)]!',
      'disabled:text-[var(--ds-fg-disabled)]!',
    );
  });
});
