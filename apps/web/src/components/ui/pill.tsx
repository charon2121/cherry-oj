import { type ComponentProps, type ReactNode } from 'react';

import { cn } from '@/lib/utils';

type PillProps = Omit<ComponentProps<'button'>, 'children'> &
  Readonly<{
    children: ReactNode;
    dot?: boolean;
    dotClassName?: string;
    selected?: boolean;
  }>;

function Pill({
  children,
  className,
  dot = false,
  dotClassName,
  onClick,
  selected = false,
  type = 'button',
  ...props
}: PillProps) {
  const classes = cn(
    // linear.app 实测的视图切换：高 28px、左右 10px、12px/500、全圆角；未选是比面板亮一档的底与
    // 灰字，选中再亮一档并换成主前景色。状态只改颜色（§7.2 B）。
    'inline-flex min-h-7 max-w-full items-center gap-1x rounded-full bg-surface shadow-ring px-[10px] font-display text-xs font-body text-fg-meta transition-colors duration-fast ease-standard focus-visible:outline-1 focus-visible:outline-offset-0 focus-visible:outline-ring motion-reduce:transition-none',
    dot && 'pl-2',
    selected && 'bg-surface-hover text-foreground',
    onClick && !selected && 'cursor-pointer hover:text-foreground',
    !onClick && 'cursor-default',
    className,
  );

  const content = (
    <>
      {dot ? (
        <span
          aria-hidden="true"
          className={cn('rounded-circle bg-brand-surface size-[6px] shrink-0', dotClassName)}
        />
      ) : null}
      <span className="min-w-0 break-words">{children}</span>
    </>
  );

  if (onClick) {
    return (
      <button
        {...props}
        type={type}
        data-slot="pill"
        aria-pressed={selected}
        className={classes}
        onClick={onClick}
      >
        {content}
      </button>
    );
  }

  return (
    <span data-slot="pill" data-selected={selected ? '' : undefined} className={classes}>
      {content}
    </span>
  );
}

export { Pill, type PillProps };
