import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import {
  Popover,
  PopoverClose,
  PopoverContent,
  PopoverDescription,
  PopoverFooter,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from './popover';

function TestPopover() {
  return (
    <>
      <Popover>
        <PopoverTrigger>查看限制说明</PopoverTrigger>
        <PopoverContent>
          <PopoverHeader>
            <PopoverTitle>本题限制</PopoverTitle>
            <PopoverDescription>
              CPU 1,000,000,000 ns，内存 268,435,456 bytes，所有单位保持明确。
            </PopoverDescription>
          </PopoverHeader>
          <a href="#full-limits">查看完整限制</a>
          <PopoverFooter>
            <PopoverClose>关闭说明</PopoverClose>
          </PopoverFooter>
        </PopoverContent>
      </Popover>
      <button type="button">下一项操作</button>
    </>
  );
}

describe('Popover', () => {
  it('opens from the trigger with Enter and Space', async () => {
    const user = userEvent.setup();
    render(<TestPopover />);
    const trigger = screen.getByRole('button', { name: '查看限制说明' });

    expect(trigger).toHaveAttribute('aria-haspopup', 'dialog');
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    trigger.focus();
    await user.keyboard('{Enter}');
    const popover = screen.getByRole('dialog', { name: '本题限制' });
    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(trigger).toHaveAttribute('aria-controls', popover.id);

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveAttribute('aria-expanded', 'false');

    trigger.focus();
    await user.keyboard(' ');
    expect(screen.getByRole('dialog', { name: '本题限制' })).toBeInTheDocument();
  });

  it('connects its accessible name and description', async () => {
    const user = userEvent.setup();
    render(<TestPopover />);

    await user.click(screen.getByRole('button', { name: '查看限制说明' }));
    const popover = screen.getByRole('dialog', { name: '本题限制' });

    expect(popover).toHaveAccessibleDescription(
      'CPU 1,000,000,000 ns，内存 268,435,456 bytes，所有单位保持明确。',
    );
  });

  it('moves focus into the disclosure and returns it on Escape', async () => {
    const user = userEvent.setup();
    render(<TestPopover />);
    const trigger = screen.getByRole('button', { name: '查看限制说明' });

    trigger.focus();
    await user.keyboard('{Enter}');
    await waitFor(() => expect(screen.getByRole('link', { name: '查看完整限制' })).toHaveFocus());

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
  });

  it('does not trap focus when used as a non-modal disclosure', async () => {
    const user = userEvent.setup();
    render(<TestPopover />);
    const trigger = screen.getByRole('button', { name: '查看限制说明' });

    trigger.focus();
    await user.keyboard('{Enter}');
    await waitFor(() => expect(screen.getByRole('link', { name: '查看完整限制' })).toHaveFocus());
    await user.tab();
    expect(screen.getByRole('button', { name: '关闭说明' })).toHaveFocus();
    await user.tab();
    await waitFor(() => expect(screen.getByRole('button', { name: '下一项操作' })).toHaveFocus());
  });
});
