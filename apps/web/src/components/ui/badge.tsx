import { cva, type VariantProps } from 'class-variance-authority';
import { Check } from 'lucide-react';
import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

const badgeVariants = cva(
  'inline-flex min-h-6 max-w-full items-center gap-1 rounded-full border px-2 py-0.5 text-[length:var(--ds-text-xs)] leading-[var(--ds-leading-tight)] font-[var(--ds-weight-body)] whitespace-normal break-words [&_svg]:size-3 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        neutral: 'border-border bg-secondary text-[var(--ds-fg-2)]',
        brand: 'border-[var(--ds-brand-foreground)] bg-brand-soft text-[var(--ds-on-brand-soft)]',
        success: 'border-[var(--ds-success-border)] bg-success-soft text-success',
        warning: 'border-[var(--ds-warning-border)] bg-warning-soft text-warning',
        danger: 'border-[var(--ds-danger-border)] bg-danger-soft text-danger',
        info: 'border-[var(--ds-info-border)] bg-info-soft text-info',
        special: 'border-[var(--ds-special-border)] bg-special-soft text-special',
      },
    },
    defaultVariants: {
      variant: 'neutral',
    },
  },
);

type BadgeProps = ComponentProps<'span'> &
  VariantProps<typeof badgeVariants> &
  Readonly<{
    selected?: boolean;
    selectedLabel?: string;
  }>;

function Badge({
  children,
  className,
  selected = false,
  selectedLabel = '已选择',
  variant = 'neutral',
  ...props
}: BadgeProps) {
  return (
    <span
      data-slot="badge"
      data-selected={selected ? '' : undefined}
      className={cn(badgeVariants({ variant, className }))}
      {...props}
    >
      {selected ? (
        <>
          <Check aria-hidden="true" />
          <span>{selectedLabel}</span>
          <span aria-hidden="true">·</span>
        </>
      ) : null}
      {children}
    </span>
  );
}

export { Badge, type BadgeProps, badgeVariants };
