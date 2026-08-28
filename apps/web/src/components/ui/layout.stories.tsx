import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { Badge } from './badge';
import { Card } from './card';
import { Cluster, Container, Section, Stack } from './layout';
import { Heading, Text } from './typography';

const meta = {
  title: 'UI/Layout',
  component: Container,
  parameters: {
    layout: 'fullscreen',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Container>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ContainerStackAndCluster: Story = {
  render: () => (
    <Container>
      <Section>
        <Stack gap={6}>
          <Stack gap={2}>
            <Heading level={2}>最近提交</Heading>
            <Text tone="muted">Container 管理页面宽度，Stack 和 Cluster 只表达排列关系。</Text>
          </Stack>
          <Cluster gap={2}>
            <Badge selected>全部</Badge>
            <Badge>已完成</Badge>
            <Badge>判题中</Badge>
          </Cluster>
          <Card>内容区域会随容器宽度自然收缩。</Card>
        </Stack>
      </Section>
    </Container>
  ),
};

export const LongChineseAtNarrowWidth: Story = {
  globals: {
    viewport: 'mobile1',
  },
  render: () => (
    <Container>
      <Section>
        <Stack gap={4}>
          <Heading level={2} size="xl">
            窄屏中的题目工作区布局审核入口
          </Heading>
          <Text>
            这段连续中文用于确认容器 gutter、垂直节奏和内容换行在三百二十像素窗口中都不会被裁切。
          </Text>
          <Cluster gap={2}>
            <Badge>字符串</Badge>
            <Badge>动态规划</Badge>
            <Badge>图论</Badge>
          </Cluster>
        </Stack>
      </Section>
    </Container>
  ),
};
