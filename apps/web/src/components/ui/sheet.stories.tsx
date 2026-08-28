import type { Meta, StoryObj } from '@storybook/tanstack-react';

import { Button } from './button';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from './sheet';

const meta = {
  title: 'UI/Sheet',
  component: Sheet,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Sheet>;

export default meta;
type Story = StoryObj<typeof meta>;

function NavigationSheet({ defaultOpen = false }: Readonly<{ defaultOpen?: boolean }>) {
  return (
    <Sheet defaultOpen={defaultOpen}>
      <SheetTrigger render={<Button variant="secondary" />}>打开管理导航</SheetTrigger>
      <SheetContent aria-labelledby="sheet-story-title" closeLabel="关闭管理导航">
        <SheetHeader>
          <SheetTitle id="sheet-story-title">管理导航</SheetTitle>
          <SheetDescription>窄屏下承载与桌面侧栏一致的导航内容。</SheetDescription>
        </SheetHeader>
        <nav aria-label="移动管理导航" className="grid gap-1 p-3">
          <a href="#dashboard" className="rounded-sm px-3 py-2 text-sm">
            Dashboard
          </a>
          <a href="#users" className="rounded-sm px-3 py-2 text-sm">
            用户账号
          </a>
        </nav>
      </SheetContent>
    </Sheet>
  );
}

export const Default: Story = {
  render: () => <NavigationSheet />,
};

export const OpenAt320: Story = {
  globals: {
    viewport: 'mobile1',
  },
  render: () => <NavigationSheet defaultOpen />,
};
