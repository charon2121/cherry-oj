import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { Bell, Plus, Settings } from 'lucide-react';

import { Badge } from './badge';
import { Button } from './button';
import { Card } from './card';
import { IconButton } from './icon-button';
import { Input } from './input';
import { Container, Stack } from './layout';
import { NavBar } from './nav-bar';
import { Pill } from './pill';
import { SearchInput } from './search-input';
import { Textarea } from './textarea';
import { Eyebrow, Heading, Text } from './typography';

const meta = {
  title: 'Foundation/Source recipes',
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'WORK-034 冻结来源中的 14 个核心配方。通过工具栏切换 Cherry Black 与 Pure White；两者共享结构、密度和状态层级。',
      },
    },
  },
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

export const CompleteSpecimen: Story = {
  render: () => (
    <div className="text-foreground min-h-screen bg-[var(--ds-canvas)]">
      <NavBar
        links={[
          { href: '#overview', label: '概览', active: true },
          { href: '#problems', label: '题目' },
          { href: '#submissions', label: '提交' },
        ]}
        actions={
          <>
            <IconButton label="通知" size="md">
              <Bell aria-hidden="true" />
            </IconButton>
            <Button size="sm">管理中心</Button>
          </>
        }
      />

      <Container className="py-[var(--ds-space-12)]">
        <Stack gap={12}>
          <Stack gap={3}>
            <Eyebrow tone="accent">Cherry OJ design system</Eyebrow>
            <Heading level={2}>克制、紧凑、内容优先</Heading>
            <Text size="lg" tone="muted" className="max-w-[760px]">
              来源配方以细边界、轻透明层级和精确排版组织界面；浅色主题只转换语义颜色，不改变布局和交互。
            </Text>
          </Stack>

          <div className="grid gap-[var(--ds-space-6)] lg:grid-cols-2">
            <Card padding="lg" radius="lg">
              <Stack gap={6}>
                <Stack gap={2}>
                  <Heading level={5}>Controls</Heading>
                  <Text size="sm" tone="meta">
                    Button、IconButton 与 Pill
                  </Text>
                </Stack>
                <Stack direction="row" gap={3} align="center" wrap>
                  <Button>创建题目</Button>
                  <Button variant="ghost">查看说明</Button>
                  <Button variant="subtle">保存草稿</Button>
                  <Button variant="toolbar" size="sm">
                    <Plus aria-hidden="true" /> 新增
                  </Button>
                  <IconButton label="设置" shape="square" active>
                    <Settings aria-hidden="true" />
                  </IconButton>
                  <Pill dot selected>
                    已发布
                  </Pill>
                  <Pill>动态规划</Pill>
                </Stack>
              </Stack>
            </Card>

            <Card padding="lg" radius="lg">
              <Stack gap={6}>
                <Stack gap={2}>
                  <Heading level={5}>Forms</Heading>
                  <Text size="sm" tone="meta">
                    Input、SearchInput 与短文本 Textarea
                  </Text>
                </Stack>
                <Input aria-label="题目标题" placeholder="题目标题" />
                <SearchInput aria-label="搜索题目" shortcut="⌘ K" />
                <Textarea aria-label="简短备注" rows={3} placeholder="仅用于简短备注" />
              </Stack>
            </Card>

            <Card padding="lg" radius="lg" interactive>
              <Stack gap={4}>
                <Badge variant="brand">推荐</Badge>
                <Heading level={4}>Typography & surfaces</Heading>
                <Text tone="default">
                  Heading、Text、Eyebrow、Badge 与 Card 使用同一套光学尺度，而不是任意 Tailwind
                  数值。
                </Text>
              </Stack>
            </Card>

            <Card padding="lg" radius="lg" elevated>
              <Stack gap={4}>
                <Eyebrow>Layout</Eyebrow>
                <Heading level={4}>Container & Stack</Heading>
                <Text size="sm" tone="muted">
                  容器负责阅读宽度和响应式 gutter，Stack 负责一维关系；浮层才使用明显 elevation。
                </Text>
              </Stack>
            </Card>
          </div>
        </Stack>
      </Container>
    </div>
  ),
};

export const NarrowChineseAndStates: Story = {
  globals: { viewport: 'mobile1' },
  render: () => (
    <div className="w-[320px] max-w-full p-[var(--ds-space-4)]">
      <Stack gap={4}>
        <Heading level={4}>窄屏中文与状态检查</Heading>
        <Button className="w-full">保存这份包含很长中文名称的题目配置</Button>
        <Input aria-label="错误输入" aria-invalid="true" defaultValue="不合法的题目标识" />
        <Button className="w-full" loading loadingLabel="正在保存题目">
          保存
        </Button>
        <Button className="w-full" disabled>
          当前不可操作
        </Button>
      </Stack>
    </div>
  ),
};
