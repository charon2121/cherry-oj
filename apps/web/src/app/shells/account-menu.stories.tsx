import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { AccountMenuView } from './account-menu';

const meta = {
  title: 'App/Navigation/AccountMenu',
  component: AccountMenuView,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof AccountMenuView>;

export default meta;
type Story = StoryObj<typeof meta>;

const baseArgs = {
  adminLink: <a href="#admin" aria-label="管理中心" />,
  defaultOpen: true,
  logoutError: null,
  logoutState: 'idle' as const,
  onLogout: () => undefined,
  passwordChangeRequired: false,
  passwordLink: <a href="#password" aria-label="修改密码" />,
  role: 'USER' as const,
  showAdminEntry: false,
  showSiteEntry: false,
  siteLink: <a href="#site" aria-label="返回用户端" />,
  username: 'alice',
};

export const User: Story = {
  args: baseArgs,
};

export const Admin: Story = {
  args: {
    ...baseArgs,
    role: 'ADMIN',
    showAdminEntry: true,
    username: 'root-admin',
  },
};

export const AdminSpace: Story = {
  args: {
    ...baseArgs,
    role: 'ADMIN',
    showSiteEntry: true,
    username: 'root-admin',
  },
};

export const PasswordChangeRequired: Story = {
  args: {
    ...baseArgs,
    passwordChangeRequired: true,
  },
};

export const LogoutError: Story = {
  args: {
    ...baseArgs,
    logoutError: '服务暂时不可用，请稍后重试。',
    logoutState: 'error',
  },
};

export const LogoutPending: Story = {
  args: {
    ...baseArgs,
    logoutState: 'pending',
  },
};

export const LongUsernameAt320: Story = {
  args: {
    ...baseArgs,
    username: 'a-very-long-cherry-oj-username-for-layout-review',
  },
  globals: {
    viewport: 'mobile1',
  },
};
