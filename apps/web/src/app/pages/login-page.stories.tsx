import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { useState } from 'react';

import { LoginPageView, type LoginPageViewProps } from './login-page';

function StatefulLoginPage(args: LoginPageViewProps) {
  const [username, setUsername] = useState(args.username);
  const [password, setPassword] = useState(args.password);

  return (
    <div className="bg-background text-foreground min-h-svh">
      <LoginPageView
        {...args}
        username={username}
        password={password}
        onUsernameChange={setUsername}
        onPasswordChange={setPassword}
      />
    </div>
  );
}

const meta = {
  title: 'App/Pages/LoginPage',
  component: LoginPageView,
  render: (args) => <StatefulLoginPage {...args} />,
  parameters: {
    layout: 'fullscreen',
  },
  tags: ['autodocs'],
  args: {
    errorMessage: undefined,
    password: '',
    pending: false,
    username: '',
    onPasswordChange: () => undefined,
    onSubmit: () => undefined,
    onUsernameChange: () => undefined,
  },
} satisfies Meta<typeof LoginPageView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Pending: Story = {
  args: {
    password: 'Secret-Password-42!',
    pending: true,
    username: 'alice',
  },
};

export const Error: Story = {
  args: {
    errorMessage: '用户名或密码错误，请检查后重试。',
    username: 'alice',
  },
};

export const LongError: Story = {
  args: {
    errorMessage:
      '暂时无法登录，请稍后再试。请求标识 request-20260829-abcdefghijklmnopqrstuvwxyz。',
    username: 'a-very-long-cherry-oj-username-for-layout-review',
  },
};

export const Mobile: Story = {
  globals: {
    viewport: 'mobile1',
  },
};
