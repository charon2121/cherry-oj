import type { Meta, StoryObj } from '@storybook/tanstack-react';
import type { ComponentProps } from 'react';

import { Badge } from './badge';

const meta = {
  title: 'UI/Badge',
  component: Badge,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  args: {
    children: '全部题目',
  },
} satisfies Meta<typeof Badge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Neutral: Story = {};

export const Variants: Story = {
  render: () => (
    <div className="flex max-w-80 flex-wrap items-center gap-2">
      <Badge variant="neutral">中性 · 全部题目</Badge>
      <Badge variant="brand">品牌 · 推荐</Badge>
      <Badge variant="success">成功 · 已保存</Badge>
      <Badge variant="warning">警告 · 即将超时</Badge>
      <Badge variant="danger">错误 · 保存失败</Badge>
      <Badge variant="info">信息 · 判题中</Badge>
      <Badge variant="special">特殊 · 系统处理</Badge>
    </div>
  ),
};

export const AsLink: Story = {
  args: {
    variant: 'brand',
    // eslint-disable-next-line jsx-a11y/anchor-has-content -- Base UI render 模式下 children 由 Badge 通过 props 注入，规则看不到
    render: (anchorProps: ComponentProps<'a'>) => <a href="/problems?tag=dp" {...anchorProps} />,
    children: '动态规划',
  },
};

export const LongChineseAtNarrowWidth: Story = {
  globals: {
    viewport: 'mobile1',
  },
  render: () => (
    <div className="w-80 max-w-full p-3">
      <Badge variant="info">信息 · 这是一段必须在窄屏中完整换行显示的很长中文状态说明</Badge>
    </div>
  ),
};
