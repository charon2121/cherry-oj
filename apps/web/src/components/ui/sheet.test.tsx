import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from './sheet';

function TestSheet() {
  return (
    <Sheet>
      <SheetTrigger>打开管理导航</SheetTrigger>
      <SheetContent aria-labelledby="test-sheet-title" closeLabel="关闭管理导航">
        <SheetHeader>
          <SheetTitle id="test-sheet-title">管理导航</SheetTitle>
          <SheetDescription>切换管理页面。</SheetDescription>
        </SheetHeader>
        <a href="#dashboard">Dashboard</a>
      </SheetContent>
    </Sheet>
  );
}

describe('Sheet', () => {
  it('closes with Escape and restores focus to its trigger', async () => {
    const user = userEvent.setup();
    render(<TestSheet />);
    const trigger = screen.getByRole('button', { name: '打开管理导航' });

    await user.click(trigger);
    expect(screen.getByRole('dialog', { name: '管理导航' })).toHaveAccessibleDescription(
      '切换管理页面。',
    );
    expect(screen.getByRole('button', { name: '关闭管理导航' })).toBeInTheDocument();

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
  });
});
