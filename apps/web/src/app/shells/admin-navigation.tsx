import { Link, useLocation } from '@tanstack/react-router';
import { ChevronRight, LayoutDashboard, Users } from 'lucide-react';
import { useState } from 'react';

import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import {
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from '@/components/ui/sidebar';

type AdminNavigationProps = Readonly<{
  onNavigate?: () => void;
}>;

function AdminNavigation({ onNavigate }: AdminNavigationProps) {
  const pathname = useLocation({ select: (location) => location.pathname });
  const [accountOpen, setAccountOpen] = useState(false);
  const dashboardActive = pathname === '/admin' || pathname === '/admin/dashborad';
  const usersActive = pathname === '/admin/users';
  const accountExpanded = usersActive || accountOpen;

  return (
    <SidebarContent>
      <SidebarGroup aria-labelledby="admin-navigation-group">
        <SidebarGroupLabel id="admin-navigation-group">管理</SidebarGroupLabel>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              render={<Link to="/admin" />}
              isActive={dashboardActive}
              aria-current={dashboardActive ? 'page' : undefined}
              onClick={onNavigate}
            >
              <LayoutDashboard aria-hidden="true" />
              <span>Dashboard</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
          <SidebarMenuItem>
            <Collapsible
              open={accountExpanded}
              onOpenChange={(open) => {
                if (!usersActive) setAccountOpen(open);
              }}
            >
              <CollapsibleTrigger
                render={
                  <SidebarMenuButton
                    className="group/account"
                    isActive={usersActive}
                    aria-label="账号管理"
                  />
                }
              >
                <Users aria-hidden="true" />
                <span>账号管理</span>
                <ChevronRight
                  aria-hidden="true"
                  className="ml-auto transition-transform duration-[var(--ds-motion-fast)] group-data-[panel-open]/account:rotate-90 motion-reduce:transition-none"
                />
              </CollapsibleTrigger>
              <CollapsibleContent>
                <SidebarMenuSub>
                  <SidebarMenuSubItem>
                    <SidebarMenuSubButton
                      render={<Link to="/admin/users" search={{ page: 1 }} />}
                      isActive={usersActive}
                      aria-current={usersActive ? 'page' : undefined}
                      onClick={onNavigate}
                    >
                      <span>用户账号</span>
                    </SidebarMenuSubButton>
                  </SidebarMenuSubItem>
                </SidebarMenuSub>
              </CollapsibleContent>
            </Collapsible>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarGroup>
    </SidebarContent>
  );
}

export { AdminNavigation, type AdminNavigationProps };
