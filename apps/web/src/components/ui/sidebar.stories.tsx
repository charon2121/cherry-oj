import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { ChevronRight, LayoutDashboard, Users } from 'lucide-react';

import { Collapsible, CollapsibleContent, CollapsibleTrigger } from './collapsible';
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from './sidebar';

const meta = {
  title: 'UI/Sidebar',
  component: Sidebar,
  parameters: {
    layout: 'fullscreen',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Sidebar>;

export default meta;
type Story = StoryObj<typeof meta>;

function NavigationExample({ defaultOpen = true }: Readonly<{ defaultOpen?: boolean }>) {
  return (
    <Sidebar aria-label="管理导航" className="min-h-96 w-60">
      <SidebarContent>
        <SidebarGroup aria-labelledby="sidebar-story-group">
          <SidebarGroupLabel id="sidebar-story-group">管理</SidebarGroupLabel>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton isActive>
                <LayoutDashboard aria-hidden="true" />
                Dashboard
              </SidebarMenuButton>
            </SidebarMenuItem>
            <SidebarMenuItem>
              <Collapsible defaultOpen={defaultOpen}>
                <CollapsibleTrigger
                  render={<SidebarMenuButton className="group/account" aria-label="账号管理" />}
                >
                  <Users aria-hidden="true" />
                  <span>账号管理</span>
                  <ChevronRight
                    aria-hidden="true"
                    className="group-data-[panel-open]/account:text-foreground duration-fast ml-auto transition-colors motion-reduce:transition-none"
                  />
                </CollapsibleTrigger>
                <CollapsibleContent>
                  <SidebarMenuSub>
                    <SidebarMenuSubItem>
                      <SidebarMenuSubButton href="#users">用户账号</SidebarMenuSubButton>
                    </SidebarMenuSubItem>
                  </SidebarMenuSub>
                </CollapsibleContent>
              </Collapsible>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroup>
      </SidebarContent>
    </Sidebar>
  );
}

export const ExpandedSubmenu: Story = {
  render: () => <NavigationExample />,
};

export const KeyboardReview: Story = {
  parameters: {
    docs: {
      description: {
        story: '使用 Tab 移动焦点，并用 Enter 或 Space 展开账号管理子菜单。',
      },
    },
  },
  render: () => <NavigationExample defaultOpen={false} />,
};
