import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { Button } from './button';

const meta = {
  title: 'UI/Button',
  component: Button,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  args: {
    children: '提交代码',
  },
} satisfies Meta<typeof Button>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Disabled: Story = {
  args: {
    disabled: true,
    children: '正在提交',
  },
};

export const Focused: Story = {
  args: {
    autoFocus: true,
  },
};

export const Dark: Story = {
  decorators: [
    (Story) => (
      <div className="dark bg-background text-foreground p-8">
        <Story />
      </div>
    ),
  ],
};

export const Destructive: Story = {
  args: {
    variant: 'destructive',
    children: '删除题目',
  },
};
