import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { Plus } from 'lucide-react';
import { useState } from 'react';

import { Button } from './button';
import { SearchInput } from './search-input';
import { Toolbar, ToolbarFilterGroup } from './toolbar';

const meta = {
  title: 'UI/Toolbar',
  component: Toolbar,
  parameters: { layout: 'padded' },
  tags: ['autodocs'],
} satisfies Meta<typeof Toolbar>;

export default meta;
type Story = StoryObj<typeof meta>;

function FilterExample() {
  const [value, setValue] = useState('all');
  return (
    <Toolbar
      title="题库"
      count="1,284 道题"
      search={<SearchInput aria-label="搜索题目" placeholder="搜索题目…" className="w-60" />}
      actions={
        <Button size="sm">
          <Plus aria-hidden="true" />
          新建题目
        </Button>
      }
      filters={
        <ToolbarFilterGroup
          label="完成状态"
          value={value}
          onValueChange={setValue}
          options={[
            { value: 'all', label: '全部' },
            { value: 'todo', label: '未通过' },
            { value: 'done', label: '已通过' },
          ]}
        />
      }
    >
      <Button variant="toolbar" size="sm">
        难度
      </Button>
      <Button variant="toolbar" size="sm">
        通过率
      </Button>
    </Toolbar>
  );
}

export const Default: Story = { args: { title: '题库' }, render: () => <FilterExample /> };

export const TitleOnly: Story = { args: { title: '账号管理', count: '12 个账号' } };
