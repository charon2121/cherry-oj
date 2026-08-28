import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { Button } from './button';
import {
  Popover,
  PopoverClose,
  PopoverContent,
  PopoverDescription,
  PopoverFooter,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from './popover';

const meta = {
  title: 'UI/Popover',
  component: Popover,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Popover>;

export default meta;
type Story = StoryObj<typeof meta>;

function StandardPopover() {
  return (
    <Popover>
      <PopoverTrigger render={<Button variant="secondary" />}>查看限制说明</PopoverTrigger>
      <PopoverContent>
        <PopoverHeader>
          <PopoverTitle>本题限制</PopoverTitle>
          <PopoverDescription>
            CPU 1,000,000,000 ns · 内存 268,435,456 bytes。限制值的单位始终写在字段名与展示中。
          </PopoverDescription>
        </PopoverHeader>
        <PopoverFooter>
          <PopoverClose render={<Button variant="ghost" size="sm" />}>关闭说明</PopoverClose>
        </PopoverFooter>
      </PopoverContent>
    </Popover>
  );
}

export const Default: Story = {
  render: () => <StandardPopover />,
};

export const OpenState: Story = {
  parameters: {
    docs: {
      description: {
        story: '显式使用 defaultOpen，供视觉回归直接审核 manifest 的 open 状态与锚点定位。',
      },
    },
  },
  render: () => (
    <Popover defaultOpen>
      <PopoverTrigger render={<Button variant="secondary" />}>查看已打开状态</PopoverTrigger>
      <PopoverContent>
        <PopoverHeader>
          <PopoverTitle>已打开的 Popover</PopoverTitle>
          <PopoverDescription>
            无需先点击 trigger，即可审核抬升面、边界、阴影与焦点状态。
          </PopoverDescription>
        </PopoverHeader>
        <PopoverFooter>
          <PopoverClose render={<Button variant="ghost" size="sm" />}>关闭说明</PopoverClose>
        </PopoverFooter>
      </PopoverContent>
    </Popover>
  ),
};

export const WithFocusableContent: Story = {
  render: () => (
    <Popover>
      <PopoverTrigger render={<Button variant="secondary" />}>查看判题环境</PopoverTrigger>
      <PopoverContent>
        <PopoverHeader>
          <PopoverTitle>判题环境</PopoverTitle>
          <PopoverDescription>当前提交使用 Go 1.25 与固定的 Linux 执行环境。</PopoverDescription>
        </PopoverHeader>
        <a
          className="focus-visible:outline-ring text-[var(--ds-link)] underline decoration-1 underline-offset-[0.18em] hover:text-[var(--ds-link-hover)] focus-visible:outline-2 focus-visible:outline-offset-2"
          href="#environment-details"
        >
          查看完整环境与标定详情
        </a>
        <PopoverFooter>
          <PopoverClose render={<Button variant="ghost" size="sm" />}>关闭说明</PopoverClose>
        </PopoverFooter>
      </PopoverContent>
    </Popover>
  ),
};

export const LongChineseAt320: Story = {
  globals: {
    viewport: 'mobile1',
  },
  parameters: {
    docs: {
      description: {
        story: '在 320px 视口审核定位、长中文换行、内部滚动和关闭操作，内容不得越出画布。',
      },
    },
  },
  render: () => (
    <Popover>
      <PopoverTrigger render={<Button variant="secondary" />}>审核窄屏说明</PopoverTrigger>
      <PopoverContent size="md">
        <PopoverHeader>
          <PopoverTitle>关于本题输入规模、输出限制与提交失败后的排查建议</PopoverTitle>
          <PopoverDescription>
            输入可能包含很长的连续中文说明和多组边界条件。若提交失败，请依次确认语言版本、时间单位、内存单位、标准输入格式以及输出是否超过平台限制；浮层必须保持清晰阅读顺序且不裁切关闭操作。
          </PopoverDescription>
        </PopoverHeader>
        <PopoverFooter>
          <PopoverClose render={<Button variant="secondary" size="sm" />}>
            我已了解限制
          </PopoverClose>
        </PopoverFooter>
      </PopoverContent>
    </Popover>
  ),
};

export const FocusVisibleReview: Story = {
  parameters: {
    docs: {
      description: {
        story:
          '使用 Tab 聚焦 trigger，按 Space 打开；键盘焦点进入内容但不被非模态 Popover 困住，Escape 关闭后返回 trigger。',
      },
    },
  },
  render: () => <StandardPopover />,
};

export const ReducedMotionReview: Story = {
  parameters: {
    docs: {
      description: {
        story:
          '在浏览器中启用 prefers-reduced-motion: reduce 后审核；Popover 定位与可用性保持不变，非必要过渡时长归零。',
      },
    },
  },
  render: () => <StandardPopover />,
};
