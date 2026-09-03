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
    'inline-flex min-h-[24px] max-w-full items-center gap-[var(--ds-space-1x)] rounded-[var(--ds-radius-pill)] border border-[var(--ds-border-solid)] px-[10px] font-display text-[length:var(--ds-text-xs)] leading-[1.8] font-[var(--ds-weight-body)] text-[var(--ds-fg-2)] transition-colors duration-[var(--ds-motion-fast)] ease-[var(--ds-ease-standard)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring motion-reduce:transition-none',
    dot ? 'pl-[var(--ds-space-2)]' : 'pl-[var(--ds-space-1)]',
    selected && 'bg-[var(--ds-surface-translucent-selected)] text-foreground',
    onClick && !selected && 'cursor-pointer hover:bg-[var(--ds-surface-translucent)]',
    !onClick && 'cursor-default',
    className,
  );

  const content = (
    <>
      {dot ? (
        <span
          aria-hidden="true"
          className={cn(
            'size-[6px] shrink-0 rounded-[var(--ds-radius-circle)] bg-[var(--ds-brand-surface)]',
            dotClassName,
          )}
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
