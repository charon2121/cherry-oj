import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { CodeText, Heading, Text } from './typography';

const meta = {
  title: 'UI/Typography',
  component: Text,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Text>;

export default meta;
type Story = StoryObj<typeof meta>;

export const TypeScale: Story = {
  render: () => (
    <div className="grid max-w-2xl gap-4">
      <Heading level={1} size="4xl">
        4xl · Cherry
      </Heading>
      <Heading level={2} size="display-lg">
        display-lg · OJ
      </Heading>
      <Heading level={1} size="3xl">
        3xl · Cherry OJ
      </Heading>
      <Heading level={2} size="2xl">
        2xl · 题目与提交
      </Heading>
      <Heading level={3} size="xl">
        xl · 最近判题结果
      </Heading>
      <Heading level={4} size="lg">
        lg · 结果摘要
      </Heading>
      <Text tone="primary" size="base">
        base · 主要正文用于需要最高阅读优先级的内容。
      </Text>
      <Text tone="muted" size="sm">
        sm · 补充说明用于解释非关键事实。
      </Text>
      <Text tone="metadata" size="xs">
        xs · 2026-08-28 · 12,400,000 ns
      </Text>
      <CodeText>memoryBytes: 8388608</CodeText>
    </div>
  ),
};

export const LongChineseAtNarrowWidth: Story = {
  globals: {
    viewport: 'mobile1',
  },
  render: () => (
    <div className="w-80 max-w-full p-3">
      <Heading level={2} size="xl">
        统计一行文本中每个字符第一次出现时对应的完整次数信息
      </Heading>
      <Text className="mt-3" tone="muted">
        这是一段用于确认中文系统字体回退、行高和连续文字换行在窄屏中仍然清晰可读的说明。
      </Text>
    </div>
  ),
};
