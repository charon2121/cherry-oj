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
    'inline-flex min-h-[24px] max-w-full items-center gap-1x rounded-full border border-border-solid px-[10px] font-display text-xs leading-[1.8] font-body text-fg-2 transition-colors duration-fast ease-standard focus-visible:outline-1 focus-visible:outline-offset-0 focus-visible:outline-ring motion-reduce:transition-none',
    dot ? 'pl-2' : 'pl-1',
    selected && 'bg-surface-translucent-selected text-foreground',
    onClick && !selected && 'cursor-pointer hover:bg-surface-translucent',
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
