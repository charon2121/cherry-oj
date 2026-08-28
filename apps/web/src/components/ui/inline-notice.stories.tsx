import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { useState } from 'react';

import { Button } from './button';
import { InlineNotice } from './inline-notice';

const meta = {
  title: 'UI/Feedback/InlineNotice',
  component: InlineNotice,
  parameters: {
    a11y: { test: 'error' },
    layout: 'centered',
  },
  tags: ['autodocs'],
  args: {
    title: '样例运行完成',
    children: '所有公开测试点均已通过。',
    variant: 'success',
  },
  argTypes: {
    variant: {
      control: 'select',
      options: ['success', 'warning', 'danger', 'info', 'special'],
    },
    live: {
      control: 'select',
      options: ['off', 'polite', 'assertive'],
    },
  },
} satisfies Meta<typeof InlineNotice>;

export default meta;
type Story = StoryObj<typeof meta>;

function DismissedNoticeReview() {
  const [visible, setVisible] = useState(false);

  if (!visible) {
    return (
      <div className="flex flex-col items-start gap-3">
        <p className="text-muted-foreground text-sm">通知已关闭；可由使用方控制是否重新显示。</p>
        <Button type="button" variant="secondary" size="sm" onClick={() => setVisible(true)}>
          重新显示通知
        </Button>
      </div>
    );
  }

  return (
    <InlineNotice
      variant="info"
      title="判题队列提示"
      action={
        <Button type="button" variant="secondary" size="sm" onClick={() => setVisible(false)}>
          关闭通知
        </Button>
      }
    >
      高峰期可能需要等待几分钟，页面会自动更新结果。
    </InlineNotice>
  );
}

export const Success: Story = {};

export const Warning: Story = {
  args: {
    variant: 'warning',
    title: '比赛即将结束',
    children: '提交后仍以服务器接收时间为准。',
  },
};

export const Danger: Story = {
  args: {
    variant: 'danger',
    title: '代码未保存',
    children: '请检查网络后重试，编辑内容仍保留在当前页面。',
    live: 'assertive',
    action: (
      <Button type="button" variant="secondary" size="sm">
        重新保存
      </Button>
    ),
  },
};

export const Info: Story = {
  args: {
    variant: 'info',
    title: '判题队列提示',
    children: '高峰期可能需要等待几分钟，页面会自动更新结果。',
  },
};

export const Special: Story = {
  args: {
    variant: 'special',
    title: '系统维护窗口',
    children: '本周六凌晨将进行例行维护，请提前保存代码。',
  },
};

export const Dismissed: Story = {
  render: () => <DismissedNoticeReview />,
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
    variant: 'warning',
    title: '比赛即将结束，请确认编译环境与提交内容',
    children:
      '当前提交仍会以服务器接收时间为准。这是一段用于检查三百二十像素窄屏换行的较长中文说明，并包含 request-20260828-abcdefghijklmnopqrstuvwxyz。',
  },
};
