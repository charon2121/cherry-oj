import { mergeProps } from '@base-ui/react/merge-props';
import { useRender } from '@base-ui/react/use-render';
import type { ComponentProps } from 'react';

import { cn } from '@/lib/utils';

function Sidebar({ className, ...props }: ComponentProps<'aside'>) {
  return (
    <aside
      data-slot="sidebar"
      className={cn(
        'text-sidebar-foreground w-sidebar border-border-soft bg-panel flex min-h-0 flex-col border-r',
        className,
      )}
      {...props}
    />
  );
}

function SidebarContent({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="sidebar-content"
      className={cn('flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto p-2', className)}
      {...props}
    />
  );
}

function SidebarGroup({ className, ...props }: ComponentProps<'section'>) {
  return (
    <section data-slot="sidebar-group" className={cn('grid min-w-0 gap-1', className)} {...props} />
  );
}

function SidebarGroupLabel({ children, className, ...props }: ComponentProps<'h2'>) {
  return (
    <h2
      data-slot="sidebar-group-label"
      className={cn('font-body text-fg-meta px-2 py-1 text-xs', className)}
      {...props}
    >
      {children}
    </h2>
  );
}

function SidebarMenu({ className, ...props }: ComponentProps<'ul'>) {
  return (
    <ul
      data-slot="sidebar-menu"
      className={cn('grid min-w-0 list-none gap-1 p-0', className)}
      {...props}
    />
  );
}

function SidebarMenuItem({ className, ...props }: ComponentProps<'li'>) {
  return <li data-slot="sidebar-menu-item" className={cn('min-w-0', className)} {...props} />;
}

// 导航项高度对齐参照的 24–27px；选中态只改背景与前景颜色，**不加粗**——
// 强调只有提亮和变灰两招（design-system.md §7.2 原则 C），加粗是第三种手段。
const menuItemClasses =
  'flex min-h-7 w-full min-w-0 items-center gap-2 rounded-xs px-2 py-1 text-left text-cap font-body text-fg-2 transition-colors duration-fast hover:bg-surface-translucent-hover hover:text-foreground focus-visible:outline-1 focus-visible:outline-offset-0 focus-visible:outline-ring data-[active]:bg-surface-hover data-[active]:text-foreground motion-reduce:transition-none [&_svg]:size-4 [&_svg]:shrink-0';

type SidebarMenuButtonProps = useRender.ComponentProps<'button'> &
  ComponentProps<'button'> &
  Readonly<{ isActive?: boolean }>;

function SidebarMenuButton({
  className,
  isActive = false,
  render,
  ...props
}: SidebarMenuButtonProps) {
  return useRender({
    defaultTagName: 'button',
    render,
    props: mergeProps<'button'>(
      {
        className: cn(menuItemClasses, className),
      },
      props,
    ),
    state: { active: isActive, slot: 'sidebar-menu-button' },
  });
}

function SidebarMenuSub({ className, ...props }: ComponentProps<'ul'>) {
  return (
    <ul
      data-slot="sidebar-menu-sub"
      className={cn(
        'border-sidebar-border ml-5 grid min-w-0 list-none gap-1 border-l py-1 pr-0 pl-3',
        className,
      )}
      {...props}
    />
  );
}

function SidebarMenuSubItem({ className, ...props }: ComponentProps<'li'>) {
  return <li data-slot="sidebar-menu-sub-item" className={cn('min-w-0', className)} {...props} />;
}

type SidebarMenuSubButtonProps = useRender.ComponentProps<'a'> &
  ComponentProps<'a'> &
  Readonly<{ isActive?: boolean }>;

function SidebarMenuSubButton({
  className,
  isActive = false,
  render,
  ...props
}: SidebarMenuSubButtonProps) {
  return useRender({
    defaultTagName: 'a',
    render,
    props: mergeProps<'a'>(
      {
        className: cn(menuItemClasses, 'min-h-9 px-2 py-1.5', className),
      },
      props,
    ),
    state: { active: isActive, slot: 'sidebar-menu-sub-button' },
  });
}

export {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  type SidebarMenuButtonProps,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  type SidebarMenuSubButtonProps,
  SidebarMenuSubItem,
};
