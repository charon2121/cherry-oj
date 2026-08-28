import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { AsyncState } from './async-state';
import { Button } from './button';

const meta = {
  title: 'UI/Feedback/AsyncState',
  component: AsyncState,
  parameters: {
    a11y: { test: 'error' },
    layout: 'centered',
  },
  tags: ['autodocs'],
  args: {
    variant: 'empty',
    size: 'panel',
    title: '暂无提交记录',
    children: '完成第一道题后，提交记录会显示在这里。',
  },
  argTypes: {
    variant: {
      control: 'select',
      options: ['empty', 'loading', 'error', 'unauthorized'],
    },
    size: {
      control: 'select',
      options: ['inline', 'panel', 'page'],
    },
  },
} satisfies Meta<typeof AsyncState>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Empty: Story = {};

export const Loading: Story = {
  args: {
    variant: 'loading',
    title: '正在读取提交详情',
    progressLabel: '提交详情加载中',
    children: '页面会在数据就绪后自动更新，不会移动键盘焦点。',
  },
};

export const Error: Story = {
  args: {
    variant: 'error',
    title: '读取失败',
    children: '请求超时；request-id-20260828 可选择复制给管理员。',
    action: (
      <Button type="button" variant="secondary" size="sm">
        重试
      </Button>
    ),
  },
};

export const UrgentErrorAnnouncement: Story = {
  args: {
    variant: 'error',
    live: 'assertive',
    title: '提交失败',
    children: '这是刚刚发生且需要立即告知用户的错误；静态错误页默认不会强制插播。',
  },
};

export const Retrying: Story = {
  args: {
    variant: 'error',
    title: '正在重试',
    children: '正在重新连接判题服务。',
    retrying: true,
  },
};

export const Unauthorized: Story = {
  args: {
    variant: 'unauthorized',
    title: '需要登录',
    children: '登录后才能查看这份提交记录。',
    action: (
      <Button type="button" variant="primary" size="sm">
        前往登录
      </Button>
    ),
  },
};

export const Sizes: Story = {
  render: () => (
    <div className="grid w-full max-w-3xl gap-6">
      <AsyncState variant="empty" size="inline" title="行内空状态">
        当前筛选条件下没有结果。
      </AsyncState>
      <AsyncState variant="empty" size="panel" title="面板空状态">
        完成第一道题后，提交记录会显示在这里。
      </AsyncState>
      <AsyncState variant="empty" size="page" title="页面空状态">
        暂时没有可以展示的内容。
      </AsyncState>
    </div>
  ),
};

export const LongChineseAt320: Story = {
  decorators: [
    (Story) => (
      <div className="w-80 max-w-full">
        <Story />
      </div>
    ),
  ],
  args: {
    variant: 'error',
    title: '读取提交详情失败，请稍后再次尝试',
    children:
      '请求已经超时。这是一段用于检查三百二十像素窄屏换行的较长中文错误详情，并包含 request-20260828-abcdefghijklmnopqrstuvwxyz。',
  },
};

export const ReducedMotionReview: Story = {
  decorators: [
    (Story) => (
      <div className="[&_[data-slot=async-state-icon]]:animate-none">
        <Story />
      </div>
    ),
  ],
  parameters: {
    docs: {
      description: {
        story: '启用系统“减弱动态效果”后，加载图标停止旋转，进度文本仍会被播报。',
      },
    },
  },
  args: {
    variant: 'loading',
    title: '正在读取提交详情',
    progressLabel: '提交详情加载中',
    children: '请在 Storybook 的 reduced-motion 媒体环境中检查静态图标。',
  },
};
