import { Tabs as TabsPrimitive } from '@base-ui/react/tabs';

import { cn } from '@/lib/utils';

// shadcn base-nova tabs（官方 registry 返回 200）：保留 Root/List/Tab/Panel 与键盘行为，
// 样式替换为本仓库的 hairline、语义颜色和不改变尺寸的焦点。
function Tabs({ className, orientation = 'horizontal', ...props }: TabsPrimitive.Root.Props) {
  return (
    <TabsPrimitive.Root
      data-slot="tabs"
      orientation={orientation}
      className={cn('flex min-w-0 flex-col', className)}
      {...props}
    />
  );
}

function TabsList({ className, ...props }: TabsPrimitive.List.Props) {
  return (
    <TabsPrimitive.List
      data-slot="tabs-list"
      className={cn('border-border-soft flex shrink-0 items-center gap-2 border-b px-4', className)}
      {...props}
    />
  );
}

function TabsTrigger({ className, ...props }: TabsPrimitive.Tab.Props) {
  return (
    <TabsPrimitive.Tab
      data-slot="tabs-trigger"
      className={cn(
        'text-fg-muted hover:text-foreground data-active:text-foreground data-active:border-foreground focus-visible:outline-ring disabled:text-fg-disabled duration-fast inline-flex min-h-10 items-center justify-center gap-2 border-b border-transparent px-3 text-sm transition-colors outline-none focus-visible:outline-1 focus-visible:outline-offset-0 motion-reduce:transition-none focus-visible:forced-colors:outline-solid [&_svg]:size-4',
        className,
      )}
      {...props}
    />
  );
}

function TabsContent({ className, ...props }: TabsPrimitive.Panel.Props) {
  return (
    <TabsPrimitive.Panel
      data-slot="tabs-content"
      className={cn(
        'focus-visible:outline-ring min-h-0 min-w-0 text-sm outline-none focus-visible:outline-1',
        className,
      )}
      {...props}
    />
  );
}

export { Tabs, TabsContent, TabsList, TabsTrigger };
