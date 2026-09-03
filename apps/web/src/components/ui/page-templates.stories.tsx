import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { AsyncState } from './async-state';
import { Button } from './button';
import { Panel } from './card';
import { DetailPageTemplate, ListPageTemplate, WorkbenchPageTemplate } from './page-templates';
import { Toolbar } from './toolbar';
import { Text } from './typography';

const meta = {
  title: 'UI/PageTemplates',
  component: ListPageTemplate,
  parameters: { layout: 'fullscreen' },
  tags: ['autodocs'],
} satisfies Meta<typeof ListPageTemplate>;

export default meta;
type Story = StoryObj<typeof meta>;

export const List: Story = {
  args: {
    title: '题库',
    toolbar: <Toolbar title="题库" count="1,284 道题" />,
    children: <Panel>列表内容</Panel>,
    pagination: <Button variant="secondary">下一批</Button>,
  },
};

export const ListEmpty: Story = {
  args: {
    title: '题库',
    toolbar: <Toolbar title="题库" count="0 道题" />,
    state: (
      <AsyncState variant="empty" size="page" title="题库还没有公开题目">
        换一组条件试试。
      </AsyncState>
    ),
  },
};

export const Detail: StoryObj<typeof DetailPageTemplate> = {
  render: () => (
    <DetailPageTemplate
      title="线段树区间和"
      titleAction={<Button size="sm">提交解答</Button>}
      aside={
        <Panel>
          <Text size="sm">元信息</Text>
        </Panel>
      }
    >
      <Panel>题面正文</Panel>
    </DetailPageTemplate>
  ),
};

export const Workbench: StoryObj<typeof WorkbenchPageTemplate> = {
  render: () => (
    <WorkbenchPageTemplate
      title="编辑题目版本"
      statusBar={
        <div className="flex items-center gap-3">
          <Text size="sm">未保存的改动</Text>
          <div className="flex-1" />
          <Button size="sm">保存</Button>
        </div>
      }
      navigation={
        <Panel>
          <Text size="sm">步骤导航</Text>
        </Panel>
      }
    >
      <Panel>工作区</Panel>
    </WorkbenchPageTemplate>
  ),
};
