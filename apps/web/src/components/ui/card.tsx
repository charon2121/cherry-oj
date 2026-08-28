import { cva, type VariantProps } from 'class-variance-authority';
import { Check } from 'lucide-react';
import { type ComponentProps, type ReactNode } from 'react';

import { cn } from '@/lib/utils';

const cardVariants = cva(
  'min-w-0 rounded-md border transition-colors duration-[var(--ds-motion-base)]',
  {
    variants: {
      variant: {
        card: 'border-border bg-surface',
        panel: 'border-border bg-[var(--ds-panel)]',
        raised: 'border-border bg-surface-raised shadow-[var(--ds-elevation-raised)]',
        interactive:
          'border-border bg-surface hover:border-border-strong hover:bg-accent has-[:focus-visible]:border-ring has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-ring',
      },
      size: {
        compact: 'p-4',
        default: 'p-5',
      },
    },
    defaultVariants: {
      variant: 'card',
      size: 'default',
    },
  },
);

type CardProps = ComponentProps<'div'> &
  VariantProps<typeof cardVariants> &
  Readonly<{
    selected?: boolean;
    selectionLabel?: string;
  }>;

function Card({
  children,
  className,
  selected = false,
  selectionLabel = '已选择',
  size = 'default',
  variant = 'card',
  ...props
}: CardProps) {
  return (
    <div
      data-slot="card"
      data-selected={selected ? '' : undefined}
      className={cn(
        cardVariants({ variant, size, className }),
        selected && 'border-border-strong bg-accent text-foreground',
      )}
      {...props}
    >
      {selected ? (
        <div
          data-slot="card-selection"
          className="mb-3 flex items-center gap-2 text-[length:var(--ds-text-sm)] font-[var(--ds-weight-body)]"
        >
          <Check className="size-4" aria-hidden="true" />
          {selectionLabel}
        </div>
      ) : null}
      {children}
    </div>
  );
}

function Panel({ className, ...props }: ComponentProps<'section'>) {
  return (
    <section
      data-slot="panel"
      className={cn('border-border min-w-0 rounded-md border bg-[var(--ds-panel)] p-5', className)}
      {...props}
    />
  );
}

function CardHeader({ className, ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-header" className={cn('grid gap-2', className)} {...props} />;
}

type CardTitleProps = Omit<ComponentProps<'h3'>, 'children'> & Readonly<{ children: ReactNode }>;

function CardTitle({ children, className, ...props }: CardTitleProps) {
  return (
    <h3
      data-slot="card-title"
      className={cn(
        'font-display text-foreground text-[length:var(--ds-text-lg)] leading-[var(--ds-leading-heading)] font-[var(--ds-weight-heading)]',
        className,
      )}
      {...props}
    >
      {children}
    </h3>
  );
}

function CardDescription({ className, ...props }: ComponentProps<'p'>) {
  return (
    <p
      data-slot="card-description"
      className={cn(
        'text-muted-foreground text-[length:var(--ds-text-sm)] leading-[var(--ds-leading-body)]',
        className,
      )}
      {...props}
    />
  );
}

function CardContent({ className, ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-content" className={cn('mt-4 min-w-0', className)} {...props} />;
}

function CardFooter({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-footer"
      className={cn('mt-5 flex flex-wrap items-center gap-2', className)}
      {...props}
    />
  );
}

export {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  type CardProps,
  CardTitle,
  type CardTitleProps,
  cardVariants,
  Panel,
};
