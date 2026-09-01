import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { FormField } from './field';
import { Input } from './input';
import { SelectField } from './select';
import { Textarea } from './textarea';

const meta = {
  title: 'UI/Field',
  component: FormField,
  parameters: { layout: 'centered' },
} satisfies Meta<typeof FormField>;

export default meta;

type Story = StoryObj<typeof meta>;

export const WithDescription: Story = {
  args: {
    label: '搜索题目',
    description: '支持中文、题号与标签组合搜索。',
    children: <Input placeholder="题号、标题或标签" />,
  },
};

export const RequiredWithError: Story = {
  args: {
    label: '题目标识',
    required: true,
    error: '错误：标识不能为空。',
    children: <Input defaultValue="" />,
  },
};

export const Disabled: Story = {
  args: {
    label: '判题环境',
    description: '比赛期间由系统锁定。',
    disabled: true,
    children: <Input defaultValue="Linux x86_64" />,
  },
};

export const MultilineInput: Story = {
  args: {
    label: '题目说明',
    description: '支持 Markdown。',
    children: <Textarea rows={4} />,
  },
};

export const SelectVariant: Story = {
  args: { label: '难度', children: <Input /> },
  render: () => (
    <SelectField
      label="难度"
      value=""
      onValueChange={() => {}}
      items={[
        { value: '', label: '全部难度' },
        { value: 'UNRATED', label: '未评级' },
        { value: 'EASY', label: '简单' },
        { value: 'MEDIUM', label: '中等' },
        { value: 'HARD', label: '困难' },
      ]}
    />
  ),
};

export const LongChineseAtNarrowWidth: Story = {
  args: { label: '占位', children: <Input /> },
  render: () => (
    <div className="w-[320px]">
      <FormField
        label="超长中文标签在窄屏下必须完整换行而不被裁切"
        description="说明文字同样需要在 320px 宽度下保持可读，不得破坏控件尺寸。"
      >
        <Input placeholder="请输入" />
      </FormField>
    </div>
  ),
};
