import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuLinkItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from './dropdown-menu';

function TestDropdownMenu() {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger>打开账号菜单</DropdownMenuTrigger>
      <DropdownMenuContent>
        <DropdownMenuGroup>
          <DropdownMenuLabel>alice</DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuLinkItem href="#password">修改密码</DropdownMenuLinkItem>
        <DropdownMenuItem disabled>暂不可用</DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="danger">退出登录</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

describe('DropdownMenu', () => {
  it('opens from the keyboard, exposes menu items, and restores focus on Escape', async () => {
    const user = userEvent.setup();
    render(<TestDropdownMenu />);
    const trigger = screen.getByRole('button', { name: '打开账号菜单' });

    trigger.focus();
    await user.keyboard('{Enter}');

    expect(screen.getByRole('menu', { name: '打开账号菜单' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: '修改密码' })).toHaveFocus();
    expect(screen.getByRole('menuitem', { name: '暂不可用' })).toHaveAttribute('data-disabled');
    expect(screen.getByRole('menuitem', { name: '退出登录' })).toHaveClass('text-destructive');

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
  });

  it('keeps disabled items readable but prevents activation', async () => {
    const user = userEvent.setup();
    render(<TestDropdownMenu />);

    await user.click(screen.getByRole('button', { name: '打开账号菜单' }));
    await waitFor(() => expect(screen.getByRole('menu', { name: '打开账号菜单' })).toHaveFocus());
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: '修改密码' })).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    const disabledItem = screen.getByRole('menuitem', { name: '暂不可用' });
    expect(disabledItem).toHaveFocus();
    expect(disabledItem).toHaveAttribute('aria-disabled', 'true');
    await user.keyboard('{Enter}');
    expect(screen.getByRole('menu', { name: '打开账号菜单' })).toBeInTheDocument();
    await user.keyboard('{ArrowDown}');

    expect(screen.getByRole('menuitem', { name: '退出登录' })).toHaveFocus();
  });
});
