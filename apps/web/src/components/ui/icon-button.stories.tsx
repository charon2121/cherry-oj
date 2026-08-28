import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { RefreshCw, Settings, Trash2 } from 'lucide-react';

import { IconButton } from './icon-button';

const meta = {
  title: 'UI/IconButton',
  component: IconButton,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  args: {
    label: '打开设置',
    children: <Settings aria-hidden="true" />,
  },
} satisfies Meta<typeof IconButton>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const VariantsAndStates: Story = {
  render: () => (
    <div className="flex items-center gap-3">
      <IconButton label="刷新结果" size="sm" variant="secondary">
        <RefreshCw aria-hidden="true" />
      </IconButton>
      <IconButton label="打开设置" variant="ghost">
        <Settings aria-hidden="true" />
      </IconButton>
      <IconButton label="删除提交" variant="danger">
        <Trash2 aria-hidden="true" />
      </IconButton>
      <IconButton label="不可用的设置" disabled>
        <Settings aria-hidden="true" />
      </IconButton>
    </div>
  ),
};

export const LongAccessibleNameAtNarrowWidth: Story = {
  globals: {
    viewport: 'mobile1',
  },
  parameters: {
    docs: {
      description: {
        story: '图标按钮保持紧凑，但完整中文 accessible name 可由读屏读取。',
      },
    },
  },
  args: {
    label: '重新运行当前题目的全部公开样例并刷新结果面板',
    children: <RefreshCw aria-hidden="true" />,
  },
};
