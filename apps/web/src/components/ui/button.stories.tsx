import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { Trash2 } from 'lucide-react';

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

export const Primary: Story = {};

export const Variants: Story = {
  render: () => (
    <div className="flex max-w-80 flex-wrap items-center gap-3">
      <Button variant="primary">提交代码</Button>
      <Button variant="secondary">运行样例</Button>
      <Button variant="secondary" aria-pressed="true">
        已选择
      </Button>
      <Button variant="ghost">查看说明</Button>
      <Button variant="danger">
        <Trash2 aria-hidden="true" />
        删除提交
      </Button>
    </div>
  ),
};

export const Sizes: Story = {
  render: () => (
    <div className="flex items-center gap-3">
      <Button size="sm">小按钮</Button>
      <Button size="md">默认按钮</Button>
    </div>
  ),
};

export const InteractionStates: Story = {
  parameters: {
    docs: {
      description: {
        story: '先按 Tab 检查 focus-visible，再检查 disabled 与 loading 的可读状态。',
      },
    },
  },
  render: () => (
    <div className="flex max-w-80 flex-wrap items-center gap-3">
      <Button>键盘焦点入口</Button>
      <Button disabled>不可用</Button>
      <Button disabled aria-pressed="true">
        已选择但不可用
      </Button>
      <Button loading loadingLabel="正在提交">
        提交代码
      </Button>
    </div>
  ),
};

export const LongChineseAtNarrowWidth: Story = {
  globals: {
    viewport: 'mobile1',
  },
  render: () => (
    <div className="w-80 max-w-full p-3">
      <Button className="w-full">
        保存这份包含很长中文名称且必须在窄屏中保持完整可读的提交配置
      </Button>
    </div>
  ),
};
