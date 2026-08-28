import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { Link } from './link';

const meta = {
  title: 'UI/Link',
  component: Link,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  args: {
    children: '查看题目详情',
    href: '#details',
  },
} satisfies Meta<typeof Link>;

export default meta;
type Story = StoryObj<typeof meta>;

export const BrandInline: Story = {};

export const MutedStandalone: Story = {
  args: {
    variant: 'muted',
    size: 'standalone',
    children: '查看历史提交',
  },
};

export const External: Story = {
  args: {
    external: true,
    size: 'standalone',
    children: '打开语言参考文档',
  },
};

export const LongChineseAtNarrowWidth: Story = {
  globals: {
    viewport: 'mobile1',
  },
  render: () => (
    <div className="w-80 max-w-full p-3 text-sm">
      需要帮助时，请阅读
      <Link href="#long-content">
        这份用于验证连续长中文链接在窄屏里仍保留下划线并且能够自然换行的完整说明
      </Link>
      。
    </div>
  ),
};
