import { Link, useLocation } from '@tanstack/react-router';
import { ChevronRight } from 'lucide-react';
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

import {
  adminNavigationEntries,
  type AdminNavigationGroup,
  type AdminNavigationLeaf,
  isAdminNavigationGroup,
  isAdminNavigationGroupActive,
  isAdminNavigationLeafActive,
} from './admin-navigation-model';

type AdminNavigationProps = Readonly<{
  onNavigate?: () => void;
}>;

type AdminNavigationLeafLinkProps = Readonly<{
  item: AdminNavigationLeaf;
  nested?: boolean;
  onNavigate: (() => void) | undefined;
  pathname: string;
}>;

function AdminNavigationLeafLink({
  item,
  nested = false,
  onNavigate,
  pathname,
}: AdminNavigationLeafLinkProps) {
  const active = isAdminNavigationLeafActive(item, pathname);
  const Icon = item.icon;
  const link =
    item.to === '/admin' ? <Link to="/admin" /> : <Link to="/admin/users" search={{ page: 1 }} />;
  const content = (
    <>
      {Icon ? <Icon aria-hidden="true" /> : null}
      <span>{item.label}</span>
    </>
  );

  if (nested) {
    return (
      <SidebarMenuSubItem>
        <SidebarMenuSubButton
          render={link}
          isActive={active}
          aria-current={active ? 'page' : undefined}
          onClick={onNavigate}
        >
          {content}
        </SidebarMenuSubButton>
      </SidebarMenuSubItem>
    );
  }

  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        render={link}
        isActive={active}
        aria-current={active ? 'page' : undefined}
        onClick={onNavigate}
      >
        {content}
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

type AdminNavigationGroupItemProps = Readonly<{
  group: AdminNavigationGroup;
  onNavigate: (() => void) | undefined;
  pathname: string;
}>;

function AdminNavigationGroupItem({ group, onNavigate, pathname }: AdminNavigationGroupItemProps) {
  const [userOpened, setUserOpened] = useState(false);
  const active = isAdminNavigationGroupActive(group, pathname);
  const expanded = active || userOpened;
  const Icon = group.icon;

  return (
    <SidebarMenuItem>
      <Collapsible
        open={expanded}
        onOpenChange={(open) => {
          if (!active) setUserOpened(open);
        }}
      >
        <CollapsibleTrigger
          render={
            <SidebarMenuButton
              className="group/admin-navigation"
              isActive={active}
              aria-label={group.label}
            />
          }
        >
          <Icon aria-hidden="true" />
          <span>{group.label}</span>
          <ChevronRight
            aria-hidden="true"
            className="ml-auto transition-transform duration-[var(--ds-motion-fast)] group-data-[panel-open]/admin-navigation:rotate-90 motion-reduce:transition-none"
          />
        </CollapsibleTrigger>
        <CollapsibleContent>
          <SidebarMenuSub>
            {group.children.map((item) => (
              <AdminNavigationLeafLink
                key={item.id}
                item={item}
                nested
                onNavigate={onNavigate}
                pathname={pathname}
              />
            ))}
          </SidebarMenuSub>
        </CollapsibleContent>
      </Collapsible>
    </SidebarMenuItem>
  );
}

function AdminNavigation({ onNavigate }: AdminNavigationProps) {
  const pathname = useLocation({ select: (location) => location.pathname });

  return (
    <SidebarContent>
      <SidebarGroup aria-labelledby="admin-navigation-group">
        <SidebarGroupLabel id="admin-navigation-group">管理</SidebarGroupLabel>
        <SidebarMenu>
          {adminNavigationEntries.map((entry) =>
            isAdminNavigationGroup(entry) ? (
              <AdminNavigationGroupItem
                key={entry.id}
                group={entry}
                onNavigate={onNavigate}
                pathname={pathname}
              />
            ) : (
              <AdminNavigationLeafLink
                key={entry.id}
                item={entry}
                onNavigate={onNavigate}
                pathname={pathname}
              />
            ),
          )}
        </SidebarMenu>
      </SidebarGroup>
    </SidebarContent>
  );
}

export { AdminNavigation, type AdminNavigationProps };
