import { Button as ButtonPrimitive } from '@base-ui/react/button';
import { cva, type VariantProps } from 'class-variance-authority';
import { LoaderCircle } from 'lucide-react';

import { cn } from '@/lib/utils';

const secondaryButtonClasses =
  'border-border-strong bg-secondary text-secondary-foreground hover:bg-accent active:bg-accent aria-pressed:border-ring aria-pressed:bg-accent disabled:border-border disabled:bg-secondary disabled:text-[var(--ds-fg-disabled)]';

const buttonVariants = cva(
  'relative inline-flex max-w-full shrink-0 cursor-pointer items-center justify-center gap-2 rounded-sm border border-transparent font-display text-[length:var(--ds-text-sm)] leading-[var(--ds-leading-tight)] font-[var(--ds-weight-body)] transition-colors duration-[var(--ds-motion-fast)] select-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:cursor-not-allowed disabled:border-border! disabled:bg-secondary! disabled:text-[var(--ds-fg-disabled)]! [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*="size-"])]:size-4',
  {
    variants: {
      variant: {
        primary:
          'bg-primary text-primary-foreground hover:bg-[var(--ds-brand-surface-hover)] active:bg-[var(--ds-brand-surface-active)] aria-pressed:bg-[var(--ds-brand-surface-active)] disabled:bg-secondary disabled:text-[var(--ds-fg-disabled)]',
        secondary: secondaryButtonClasses,
        ghost:
          'bg-transparent text-[var(--ds-fg-2)] hover:bg-accent hover:text-foreground active:bg-accent aria-pressed:bg-accent aria-pressed:text-foreground disabled:bg-secondary disabled:text-[var(--ds-fg-disabled)]',
        danger:
          'bg-destructive text-destructive-foreground hover:border-[var(--ds-danger-on-solid)] active:border-[var(--ds-danger-on-solid)] aria-pressed:border-[var(--ds-danger-on-solid)] disabled:border-border disabled:bg-secondary disabled:text-[var(--ds-fg-disabled)]',
      },
      size: {
        sm: 'min-h-8 px-3 py-1.5',
        md: 'min-h-10 px-4 py-2',
      },
    },
    defaultVariants: {
      variant: 'primary',
      size: 'md',
    },
  },
);

type ButtonProps = ButtonPrimitive.Props &
  VariantProps<typeof buttonVariants> &
  Readonly<{
    loading?: boolean;
    loadingLabel?: string;
  }>;

function Button({
  'aria-label': ariaLabel,
  children,
  className,
  disabled,
  loading = false,
  loadingLabel = '处理中…',
  size = 'md',
  type = 'button',
  variant = 'primary',
  ...props
}: ButtonProps) {
  return (
    <ButtonPrimitive
      {...props}
      type={type}
      data-slot="button"
      data-loading={loading ? '' : undefined}
      aria-label={loading ? loadingLabel : ariaLabel}
      aria-busy={loading || undefined}
      className={cn(buttonVariants({ variant, size, className }))}
      disabled={disabled || loading}
    >
      <span
        className={cn(
          'inline-flex min-w-0 items-center justify-center gap-2 whitespace-normal',
          loading && 'invisible',
        )}
        aria-hidden={loading || undefined}
      >
        {children}
      </span>
      {loading ? (
        <span
          className="absolute inset-0 inline-flex items-center justify-center gap-2"
          role="status"
        >
          <LoaderCircle className="animate-spin motion-reduce:animate-none" aria-hidden="true" />
          {loadingLabel}
        </span>
      ) : null}
    </ButtonPrimitive>
  );
}

export { Button, type ButtonProps, buttonVariants };
