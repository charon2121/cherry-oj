import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { Badge } from './badge';
import {
  Card,
  CardAction,
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
} satisfies Meta<typeof Card>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <Card className="w-80">
      <CardHeader>
        <CardTitle>字符统计</CardTitle>
        <CardDescription>统计一行文本中每个字符出现的次数。</CardDescription>
        <CardAction>
          <Badge variant="success">已通过</Badge>
        </CardAction>
      </CardHeader>
      <CardContent>难度：入门 · 通过率 72%</CardContent>
      <CardFooter>
        <Link href="/problems/1">查看题目</Link>
      </CardFooter>
    </Card>
  ),
};

export const SmallSize: Story = {
  render: () => (
    <Card size="sm" className="w-72">
      <CardHeader>
        <CardTitle>紧凑尺寸</CardTitle>
        <CardDescription>官方 size=&quot;sm&quot; 收窄 --card-spacing。</CardDescription>
      </CardHeader>
      <CardContent>用于信息密度更高的列表。</CardContent>
    </Card>
  ),
};

export const PanelGrouping: Story = {
  render: () => (
    <Panel className="w-80">
      <h2 className="text-[length:var(--ds-text-base)] font-[var(--ds-weight-heading)]">
        提交记录
      </h2>
      <p className="text-muted-foreground mt-1 text-[length:var(--ds-text-sm)]">
        Panel 是 Cherry OJ 自有容器，官方没有对应组件；普通分组优先用它而不是 Card 的抬升面。
      </p>
    </Panel>
  ),
};

export const LongChineseAtNarrowWidth: Story = {
  render: () => (
    <Card className="w-[320px]">
      <CardHeader>
        <CardTitle>超长中文标题在窄屏下必须完整换行而不被裁切或撑破容器</CardTitle>
        <CardDescription>
          验证 320px 下的换行行为，长中文不得破坏卡片尺寸或让操作区被挤出可视范围。
        </CardDescription>
      </CardHeader>
      <CardContent>正文同样需要在窄宽度下保持可读。</CardContent>
    </Card>
  ),
};
