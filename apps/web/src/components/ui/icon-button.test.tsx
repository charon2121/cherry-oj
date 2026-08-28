import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Settings } from 'lucide-react';
import { describe, expect, it, vi } from 'vitest';

import { IconButton } from './icon-button';

describe('IconButton', () => {
  it('requires a visible-to-assistive-technology name and activates normally', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <IconButton label="打开设置" onClick={onClick}>
        <Settings aria-hidden="true" />
      </IconButton>,
    );

    const button = screen.getByRole('button', { name: '打开设置' });
    await user.click(button);
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('does not activate when disabled', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <IconButton label="不可用的设置" disabled onClick={onClick}>
        <Settings aria-hidden="true" />
      </IconButton>,
    );

    await user.click(screen.getByRole('button', { name: '不可用的设置' }));
    expect(onClick).not.toHaveBeenCalled();
  });
});
