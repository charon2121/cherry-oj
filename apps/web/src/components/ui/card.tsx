import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

// Card 系列骨架取自 shadcn base-nova 官方 card：保留官方的七个子组件、`--card-spacing`
// 间距变量、`size` 尺寸和 CardAction 的网格定位。相对官方只改颜色相关 class：
//   - 官方 `ring-1 ring-foreground/10` 与 `bg-muted/50` 用透明度叠加承担边界与分层，
//     design-system.md §4 禁止；改为 border-border 与 surface-subtle 两个语义 token。
//   - 官方 `cn-font-heading` 是其自带字体工具类，本仓库字体由 foundation token 提供。
function Card({
  className,
  size = 'default',
  ...props
}: ComponentProps<'div'> & { size?: 'default' | 'sm' }) {
  return (
    <div
      data-slot="card"
      data-size={size}
      className={cn(
        'group/card border-border bg-card text-card-foreground flex min-w-0 flex-col gap-(--card-spacing) overflow-hidden rounded-md border py-(--card-spacing) text-sm [--card-spacing:--spacing(5)] has-data-[slot=card-footer]:pb-0 has-[>img:first-child]:pt-0 data-[size=sm]:[--card-spacing:--spacing(4)] data-[size=sm]:has-data-[slot=card-footer]:pb-0 *:[img:first-child]:rounded-t-md *:[img:last-child]:rounded-b-md',
        className,
      )}
      {...props}
    />
  );
}

function CardHeader({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-header"
      className={cn(
        'group/card-header grid auto-rows-min items-start gap-1 rounded-t-md px-(--card-spacing) has-data-[slot=card-action]:grid-cols-[1fr_auto] has-data-[slot=card-description]:grid-rows-[auto_auto] [.border-b]:pb-(--card-spacing)',
        className,
      )}
      {...props}
    />
  );
}

function CardTitle({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-title"
      className={cn(
        'text-[length:var(--ds-text-base)] leading-[var(--ds-leading-snug)] font-[var(--ds-weight-heading)] group-data-[size=sm]/card:text-[length:var(--ds-text-sm)]',
        className,
      )}
      {...props}
    />
  );
}

function CardDescription({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-description"
      className={cn('text-muted-foreground text-[length:var(--ds-text-sm)]', className)}
      {...props}
    />
  );
}

function CardAction({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-action"
      className={cn('col-start-2 row-span-2 row-start-1 self-start justify-self-end', className)}
      {...props}
    />
  );
}

function CardContent({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div data-slot="card-content" className={cn('px-(--card-spacing)', className)} {...props} />
  );
}

function CardFooter({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-footer"
      className={cn(
        'border-border bg-surface-subtle flex items-center rounded-b-md border-t p-(--card-spacing)',
        className,
      )}
      {...props}
    />
  );
}

// Panel 是 Cherry OJ 自有概念，官方 registry 没有对应组件：design-system.md §2 要求普通分组
// 优先用间距、对齐和分隔线，而不是 Card 的抬升面。它是仓库里实际在用的容器（14 处）。
function Panel({ className, ...props }: ComponentProps<'section'>) {
  return (
    <section
      data-slot="panel"
      className={cn('border-border min-w-0 rounded-md border bg-[var(--ds-panel)] p-5', className)}
      {...props}
    />
  );
}

export { Card, CardAction, CardContent, CardDescription, CardFooter, CardHeader, CardTitle, Panel };
