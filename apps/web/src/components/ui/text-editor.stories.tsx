import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { useState } from 'react';

import { FormField } from './field';
import { TextEditor } from './text-editor';

const meta = {
  title: 'UI/TextEditor',
  component: TextEditor,
  args: {
    value: '# A + B\n\n请编写程序计算两个整数之和。',
    onChange: () => undefined,
    language: 'markdown',
    size: 'compact',
    'aria-label': '题目正文 Markdown',
  },
} satisfies Meta<typeof TextEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

function ControlledEditor() {
  const [value, setValue] = useState('# A + B\n\n请编写程序计算两个整数之和。');
  return (
    <FormField label="题目正文" required description="支持 Markdown，使用 Ctrl/Cmd+F 搜索。">
      <TextEditor
        value={value}
        onChange={setValue}
        language="markdown"
        size="compact"
        aria-label="题目正文 Markdown"
      />
    </FormField>
  );
}

export const Default: Story = { render: () => <ControlledEditor /> };

export const Invalid: Story = {
  render: () => (
    <FormField label="参考程序" required invalid error="请输入参考程序。">
      <TextEditor
        value=""
        onChange={() => undefined}
        language="cpp"
        size="compact"
        aria-invalid="true"
        aria-label="C++ 参考程序"
      />
    </FormField>
  ),
};
