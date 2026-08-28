import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { Badge } from './badge';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
  Panel,
} from './card';
import { Link } from './link';

const meta = {
  title: 'UI/CardPanel',
  component: Card,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Card>;

export default meta;
type Story = StoryObj<typeof meta>;

function CardExample() {
  return (
    <>
      <CardHeader>
        <CardTitle>P1042 · 字符统计</CardTitle>
        <CardDescription>统计一行文本中每个字符出现的次数。</CardDescription>
      </CardHeader>
      <CardContent>时间限制 1,000,000,000 ns</CardContent>
      <CardFooter>
        <Badge>字符串</Badge>
        <Badge>入门</Badge>
      </CardFooter>
    </>
  );
}

export const Variants: Story = {
  render: () => (
    <div className="grid max-w-3xl grid-cols-1 gap-4 sm:grid-cols-2">
      <Card variant="card">
        <CardExample />
      </Card>
      <Card variant="panel">
        <CardExample />
      </Card>
      <Card variant="raised">
        <CardExample />
      </Card>
      <Card variant="interactive">
        <CardHeader>
          <CardTitle>
            <Link href="#problem">P1042 · 打开题目</Link>
          </CardTitle>
          <CardDescription>按 Tab 检查真实链接与卡片 focus-visible。</CardDescription>
        </CardHeader>
      </Card>
    </div>
  ),
};

export const Sizes: Story = {
  render: () => (
    <div className="grid max-w-xl gap-4">
      <Card size="compact">
        <CardTitle>紧凑卡片</CardTitle>
      </Card>
      <Card size="default">
        <CardTitle>默认卡片</CardTitle>
      </Card>
    </div>
  ),
};

export const Selected: Story = {
  render: () => (
    <Card selected selectionLabel="当前题目" className="max-w-sm">
      <CardExample />
    </Card>
  ),
};

export const PanelRegion: Story = {
  render: () => (
    <Panel aria-labelledby="example-panel-title" className="max-w-sm">
      <CardTitle id="example-panel-title">判题环境</CardTitle>
      <CardDescription>Panel 是稳定分区，不伪装成可点击卡片。</CardDescription>
    </Panel>
  ),
};

export const LongChineseAtNarrowWidth: Story = {
  globals: {
    viewport: 'mobile1',
  },
  render: () => (
    <div className="w-80 max-w-full p-3">
      <Card>
        <CardTitle>这是一道标题非常长且需要在三百二十像素窄屏中自然换行的字符串处理题目</CardTitle>
        <CardDescription>
          内容不能溢出卡片，也不能因为主题变化而丢失标题、描述与标签之间的清晰层级。
        </CardDescription>
      </Card>
    </div>
  ),
};
