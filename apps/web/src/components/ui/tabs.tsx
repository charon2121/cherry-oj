import { Tabs as TabsPrimitive } from '@base-ui/react/tabs';

import { cn } from '@/lib/utils';

// shadcn base-nova tabs（官方 registry 返回 200）：保留 Root/List/Tab/Panel 与键盘行为。
// 外观是 linear.app 的视图切换胶囊（docs/design-system/measurements.md「视图切换胶囊」）：
// 高 28px、左右 10px、12px/500、全圆角；未选是灰字，选中换成亮一档的底与主前景色。没有下划线。
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
      className={cn('flex shrink-0 flex-wrap items-center gap-1 px-4 py-2', className)}
      {...props}
    />
  );
}

function TabsTrigger({ className, ...props }: TabsPrimitive.Tab.Props) {
  return (
    <TabsPrimitive.Tab
      data-slot="tabs-trigger"
      className={cn(
        'text-fg-meta font-body hover:text-foreground data-active:bg-surface-hover data-active:text-foreground focus-visible:outline-ring disabled:text-fg-disabled duration-fast gap-1x inline-flex h-7 items-center justify-center rounded-full px-[10px] text-xs transition-colors outline-none focus-visible:outline-1 focus-visible:outline-offset-0 motion-reduce:transition-none focus-visible:forced-colors:outline-solid data-active:forced-colors:outline-1 data-active:forced-colors:outline-solid [&_svg]:size-3.5',
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
