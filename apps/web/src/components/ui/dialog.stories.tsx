import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { Trash2 } from 'lucide-react';

import { Button } from './button';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from './dialog';

const meta = {
  title: 'UI/Dialog',
  component: Dialog,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Dialog>;

export default meta;
type Story = StoryObj<typeof meta>;

function StandardDialog() {
  return (
    <Dialog>
      <DialogTrigger render={<Button variant="secondary" />}>查看通知</DialogTrigger>
      <DialogContent size="sm">
        <DialogHeader>
          <DialogTitle>通知中心</DialogTitle>
          <DialogDescription>所有系统通知都已读完，可以继续专注当前题目。</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <DialogClose render={<Button variant="secondary" />}>知道了</DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export const Default: Story = {
  render: () => <StandardDialog />,
};

export const OpenState: Story = {
  parameters: {
    docs: {
      description: {
        story: '显式使用 defaultOpen，供视觉回归直接审核 manifest 的 open 状态。',
      },
    },
  },
  render: () => (
    <Dialog defaultOpen>
      <DialogTrigger render={<Button variant="secondary" />}>查看已打开状态</DialogTrigger>
      <DialogContent size="sm">
        <DialogHeader>
          <DialogTitle>已打开的对话框</DialogTitle>
          <DialogDescription>
            无需先点击 trigger，即可审核遮罩、抬升面、边界和焦点状态。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <DialogClose render={<Button variant="secondary" />}>关闭</DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  ),
};

export const Confirmation: Story = {
  render: () => (
    <Dialog>
      <DialogTrigger render={<Button variant="danger" />}>
        <Trash2 aria-hidden="true" />
        删除提交
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>确认删除这次提交？</DialogTitle>
          <DialogDescription>
            删除后不能恢复。源代码与判题结果将从当前列表移除，这项危险操作使用明确动词和图标表达。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <DialogClose render={<Button variant="secondary" />}>取消</DialogClose>
          <DialogClose render={<Button variant="danger" />}>
            <Trash2 aria-hidden="true" />
            确认删除提交
          </DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  ),
};

export const LongChineseAt320: Story = {
  globals: {
    viewport: 'mobile1',
  },
  parameters: {
    docs: {
      description: {
        story: '在 320px 视口审核标题、正文、关闭按钮和纵向操作区均不被裁切。',
      },
    },
  },
  render: () => (
    <Dialog>
      <DialogTrigger render={<Button variant="secondary" />}>审核窄屏长中文</DialogTrigger>
      <DialogContent className="max-w-[calc(320px-var(--space-6))]">
        <DialogHeader>
          <DialogTitle>
            确认离开这道包含非常长中文标题、连续技术说明和多项边界条件的在线判题练习题？
          </DialogTitle>
          <DialogDescription>
            当前尚未提交的代码会保留在本地编辑状态中，但服务器不会收到这次修改。请确认已经复制需要保留的错误详情、资源事实与调试记录，然后再决定是否离开。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <DialogClose render={<Button variant="secondary" />}>继续编辑</DialogClose>
          <DialogClose render={<Button variant="primary" />}>确认离开当前题目</DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  ),
};

export const FocusVisibleReview: Story = {
  parameters: {
    docs: {
      description: {
        story:
          '使用 Tab 聚焦 trigger，按 Enter 打开；焦点应留在对话框内，Escape 关闭后返回 trigger，所有焦点轮廓保持可见。',
      },
    },
  },
  render: () => <StandardDialog />,
};

export const ReducedMotionReview: Story = {
  parameters: {
    docs: {
      description: {
        story:
          '在浏览器中启用 prefers-reduced-motion: reduce 后审核；浮层仍完成开关，但不应出现非必要的缩放或淡入淡出时长。',
      },
    },
  },
  render: () => <StandardDialog />,
};
