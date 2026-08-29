import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { KeyRound, LogOut, UserRound } from 'lucide-react';

import { Button } from './button';
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

const meta = {
  title: 'UI/DropdownMenu',
  component: DropdownMenu,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof DropdownMenu>;

export default meta;
type Story = StoryObj<typeof meta>;

function AccountMenuExample({ defaultOpen = false }: Readonly<{ defaultOpen?: boolean }>) {
  return (
    <DropdownMenu defaultOpen={defaultOpen}>
      <DropdownMenuTrigger render={<Button variant="ghost" size="sm" />}>
        <UserRound aria-hidden="true" />
        root-admin
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        <DropdownMenuGroup>
          <DropdownMenuLabel>
            <span>登录账号</span>
            <span className="text-foreground text-sm font-[var(--ds-weight-heading)]">
              root-admin
            </span>
          </DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuGroup>
          <DropdownMenuLinkItem href="#password">
            <KeyRound aria-hidden="true" />
            修改密码
          </DropdownMenuLinkItem>
          <DropdownMenuItem disabled>暂不可用</DropdownMenuItem>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="danger">
          <LogOut aria-hidden="true" />
          退出登录
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export const Default: Story = {
  render: () => <AccountMenuExample />,
};

export const OpenState: Story = {
  render: () => <AccountMenuExample defaultOpen />,
};

export const At320: Story = {
  globals: {
    viewport: 'mobile1',
  },
  render: () => <AccountMenuExample defaultOpen />,
};

export const KeyboardReview: Story = {
  parameters: {
    docs: {
      description: {
        story: '使用 Enter 打开、方向键移动、Escape 关闭；禁用项可被读出，但不能激活。',
      },
    },
  },
  render: () => <AccountMenuExample />,
};
