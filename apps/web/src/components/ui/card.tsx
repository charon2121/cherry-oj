import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

// Card 系列骨架取自 shadcn base-nova 官方 card：保留官方的七个子组件、`--card-spacing`
// 间距变量、`size` 尺寸和 CardAction 的网格定位。相对官方只改颜色相关 class：
//   - 官方 `ring-1 ring-foreground/10` 与 `bg-muted/50` 用透明度叠加承担边界与分层，
//     design-system.md §4 禁止；改为 border-border 与 surface-subtle 两个语义 token。
//   - 官方 `cn-font-heading` 是其自带字体工具类，本仓库字体由 foundation token 提供。
function Card({
  className,
  elevated = false,
  interactive = false,
  padding,
  radius = 'md',
  size = 'default',
  ...props
}: ComponentProps<'div'> & {
  elevated?: boolean;
  interactive?: boolean;
  padding?: 'sm' | 'md' | 'lg';
  radius?: 'md' | 'lg' | 'xl';
  size?: 'default' | 'sm';
}) {
  const resolvedPadding = padding ?? (size === 'sm' ? 'sm' : 'md');
  return (
    <div
      data-slot="card"
      data-size={size}
      className={cn(
        'group/card text-card-foreground flex min-w-0 flex-col overflow-hidden border border-[var(--ds-border)] bg-[var(--ds-surface-translucent)] transition-colors duration-[var(--ds-motion-base)] ease-[var(--ds-ease-standard)] motion-reduce:transition-none',
        resolvedPadding === 'sm' && 'gap-[var(--ds-space-4)] p-[var(--ds-space-4)]',
        resolvedPadding === 'md' && 'gap-[var(--ds-space-6)] p-[var(--ds-space-6)]',
        resolvedPadding === 'lg' && 'gap-[var(--ds-space-8)] p-[var(--ds-space-8)]',
        radius === 'md' && 'rounded-[var(--ds-radius-md)]',
        radius === 'lg' && 'rounded-[var(--ds-radius-lg)]',
        radius === 'xl' && 'rounded-[var(--ds-radius-xl)]',
        interactive && 'hover:bg-[var(--ds-surface-translucent-hover)]',
        elevated && 'shadow-[var(--ds-elevation-raised)]',
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
        'group/card-header grid min-w-0 auto-rows-min items-start gap-[var(--ds-space-1)] has-data-[slot=card-action]:grid-cols-[minmax(0,1fr)_auto] has-data-[slot=card-description]:grid-rows-[auto_auto]',
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
        'font-display text-[length:var(--ds-text-base)] leading-[var(--ds-leading-h2)] font-[var(--ds-weight-heading)] tracking-[var(--ds-tracking-heading)] group-data-[size=sm]/card:text-[length:var(--ds-text-sm)]',
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
  return <div data-slot="card-content" className={cn('min-w-0', className)} {...props} />;
}

function CardFooter({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-footer"
      className={cn(
        'flex min-w-0 flex-wrap items-center gap-[var(--ds-space-2)] border-t border-[var(--ds-border)] pt-[var(--ds-space-4)]',
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
      className={cn(
        'min-w-0 rounded-[var(--ds-radius-lg)] border border-[var(--ds-border)] bg-[var(--ds-panel)] p-[var(--ds-space-5)]',
        className,
      )}
      {...props}
    />
  );
}

export { Card, CardAction, CardContent, CardDescription, CardFooter, CardHeader, CardTitle, Panel };
