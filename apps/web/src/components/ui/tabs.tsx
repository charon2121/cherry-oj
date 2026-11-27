import { Tabs as TabsPrimitive } from '@base-ui/react/tabs';

import { cn } from '@/lib/utils';

// 分段控制器式 Tabs：底层基于 Base UI Tabs primitive，外观为柔和底槽加抬升选中块。
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
      className={cn(
        'bg-surface-subtle inline-flex w-fit max-w-full shrink-0 flex-wrap items-center gap-1 rounded-lg p-1',
        className,
      )}
      {...props}
    />
  );
}

function TabsTrigger({ className, ...props }: TabsPrimitive.Tab.Props) {
  return (
    <TabsPrimitive.Tab
      data-slot="tabs-trigger"
      className={cn(
        'text-fg-muted font-body hover:text-foreground data-active:bg-panel data-active:text-foreground data-active:shadow-ring focus-visible:outline-ring disabled:text-fg-disabled duration-fast inline-flex h-7 cursor-pointer items-center justify-center gap-1.5 rounded-md px-3 text-xs transition-[background-color,color,box-shadow] outline-none focus-visible:outline-1 focus-visible:outline-offset-0 motion-reduce:transition-none focus-visible:forced-colors:outline-solid data-active:forced-colors:outline-1 data-active:forced-colors:outline-solid [&_svg]:size-3.5',
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
