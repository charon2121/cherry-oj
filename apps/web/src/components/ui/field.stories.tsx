import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { Field, Input, Select, Textarea } from './field';

const meta = {
  title: 'UI/Field',
  component: Field,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  args: {
    label: '搜索题目',
    description: '支持中文、题号与标签组合搜索。',
    children: <Input placeholder="题号、标题或标签" />,
  },
} satisfies Meta<typeof Field>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Invalid: Story = {
  args: {
    label: '编程语言',
    description: undefined,
    error: '错误：提交前必须选择一种语言。',
    required: true,
    children: (
      <Select defaultValue="">
        <option value="">请选择语言</option>
        <option value="go">Go</option>
        <option value="java">Java</option>
      </Select>
    ),
  },
};

export const Disabled: Story = {
  args: {
    label: '判题环境',
    description: '不可用状态仍保留清晰文字与控件边界。',
    disabled: true,
    children: <Input defaultValue="比赛期间由系统锁定" />,
  },
};

export const ControlVariants: Story = {
  render: () => (
    <div className="grid w-80 max-w-full gap-5">
      <Field label="搜索题目" description="Input / search">
        <Input type="search" placeholder="输入题号或标题" />
      </Field>
      <Field label="编程语言" description="Native select">
        <Select defaultValue="go">
          <option value="go">Go</option>
          <option value="java">Java</option>
        </Select>
      </Field>
      <Field label="补充说明" description="Textarea">
        <Textarea placeholder="写下必要的补充说明" />
      </Field>
    </div>
  ),
};

export const LongChineseAtNarrowWidth: Story = {
  globals: {
    viewport: 'mobile1',
  },
  render: () => (
    <div className="w-80 max-w-full p-3">
      <Field
        label="用于验证极长中文标签在窄屏中仍然完整可读的题目筛选条件"
        description="说明文字会自然换行，placeholder 不会替代上方的真实标签。"
      >
        <Input placeholder="请输入完整的筛选关键字" />
      </Field>
    </div>
  ),
};
