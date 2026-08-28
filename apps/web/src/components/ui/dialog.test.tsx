import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from './dialog';

function TestDialog() {
  return (
    <Dialog>
      <DialogTrigger>打开删除确认</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>确认删除提交？</DialogTitle>
          <DialogDescription>删除后不能恢复，源代码和判题结果会从列表中移除。</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <button type="button">取消</button>
          <button type="button">确认删除提交</button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

describe('Dialog', () => {
  it('opens from the trigger with Enter and Space', async () => {
    const user = userEvent.setup();
    render(<TestDialog />);
    const trigger = screen.getByRole('button', { name: '打开删除确认' });

    expect(trigger).toHaveAttribute('aria-haspopup', 'dialog');
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    trigger.focus();
    await user.keyboard('{Enter}');
    const dialog = screen.getByRole('dialog', { name: '确认删除提交？' });
    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(trigger).toHaveAttribute('aria-controls', dialog.id);

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveAttribute('aria-expanded', 'false');

    trigger.focus();
    await user.keyboard(' ');
    expect(screen.getByRole('dialog', { name: '确认删除提交？' })).toBeInTheDocument();
  });

  it('connects its accessible name and description', async () => {
    const user = userEvent.setup();
    render(<TestDialog />);

    await user.click(screen.getByRole('button', { name: '打开删除确认' }));
    const dialog = screen.getByRole('dialog', { name: '确认删除提交？' });

    expect(dialog).toHaveAccessibleDescription('删除后不能恢复，源代码和判题结果会从列表中移除。');
    expect(screen.getByRole('button', { name: '关闭对话框' })).toBeInTheDocument();
  });

  it('traps focus and restores it to the trigger after Escape', async () => {
    const user = userEvent.setup();
    render(<TestDialog />);
    const trigger = screen.getByRole('button', { name: '打开删除确认' });

    await user.click(trigger);
    const cancel = screen.getByRole('button', { name: '取消' });
    const close = screen.getByRole('button', { name: '关闭对话框' });
    await waitFor(() => expect(cancel).toHaveFocus());

    await user.tab({ shift: true });
    await waitFor(() => expect(close).toHaveFocus());
    await user.tab();
    await waitFor(() => expect(cancel).toHaveFocus());

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
  });
});
