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
    'border-border-solid bg-surface inline-flex min-h-8 max-w-full items-center gap-1.5 rounded-lg border px-2.5 font-display text-xs font-body text-fg-muted transition-[background-color,color,border-color,box-shadow] duration-fast ease-standard focus-visible:outline-1 focus-visible:outline-offset-0 focus-visible:outline-ring motion-reduce:transition-none',
    dot && 'pl-2',
    selected &&
      'border-border-strong bg-surface-subtle text-foreground shadow-ring forced-colors:outline-1',
    onClick && !selected && 'cursor-pointer hover:bg-surface-hover hover:text-foreground',
    !onClick && 'cursor-default',
    className,
  );

  const content = (
    <>
      {dot ? (
        <span
          aria-hidden="true"
          className={cn('rounded-circle bg-brand-surface size-2 shrink-0', dotClassName)}
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
