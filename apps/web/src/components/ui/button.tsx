import { Button as ButtonPrimitive } from '@base-ui/react/button';
import { cva, type VariantProps } from 'class-variance-authority';
import { LoaderCircle } from 'lucide-react';

import { cn } from '@/lib/utils';

const secondaryButtonClasses =
  'bg-[var(--ds-surface-translucent-hover)] text-[var(--ds-fg-2)] hover:bg-[var(--ds-surface-translucent-selected)] active:bg-[var(--ds-surface-translucent)] aria-pressed:bg-[var(--ds-surface-translucent-selected)]';

const buttonVariants = cva(
  'relative inline-flex max-w-full shrink-0 cursor-pointer items-center justify-center gap-[var(--ds-space-2)] rounded-[var(--ds-radius-sm)] border border-transparent font-display leading-[var(--ds-leading-tight)] font-[var(--ds-weight-body)] transition-[background-color,color,border-color] duration-[var(--ds-motion-fast)] ease-[var(--ds-ease-standard)] select-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:pointer-events-none disabled:cursor-not-allowed disabled:border-[var(--ds-border)]! disabled:bg-[var(--ds-surface-translucent)]! disabled:text-[var(--ds-fg-disabled)]! motion-reduce:transition-none [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*="size-"])]:size-4',
  {
    variants: {
      variant: {
        primary:
          'bg-primary text-primary-foreground hover:bg-[var(--ds-brand-surface-hover)] active:bg-[var(--ds-brand-surface-active)] aria-pressed:bg-[var(--ds-brand-surface-active)] disabled:bg-secondary disabled:text-[var(--ds-fg-disabled)]',
        secondary: secondaryButtonClasses,
        ghost:
          'border-[var(--ds-border-solid)] bg-[var(--ds-surface-translucent)] text-[var(--ds-fg-ghost)] hover:bg-[var(--ds-surface-translucent-selected)] active:bg-[var(--ds-surface-translucent)] aria-pressed:bg-[var(--ds-surface-translucent-selected)] aria-pressed:text-foreground',
        subtle: secondaryButtonClasses,
        toolbar:
          'rounded-[var(--ds-radius-micro)] border-[var(--ds-border-soft)] bg-[var(--ds-surface-translucent-selected)] text-[var(--ds-fg-meta)] shadow-[var(--ds-elevation-subtle)] hover:bg-[var(--ds-surface-hover)] hover:text-[var(--ds-fg-2)] active:bg-[var(--ds-surface-translucent-selected)] aria-pressed:text-foreground',
        danger:
          'bg-destructive text-destructive-foreground hover:bg-[var(--ds-danger-foreground)] active:bg-[var(--ds-danger-solid)] aria-pressed:outline-2 aria-pressed:outline-offset-[-2px] aria-pressed:outline-[var(--ds-danger-on-solid)]',
      },
      size: {
        sm: 'h-6 px-[var(--ds-space-1x)] text-[length:var(--ds-text-xs)]',
        md: 'min-h-8 px-[var(--ds-space-4)] py-[var(--ds-space-2)] text-[length:var(--ds-text-sm)]',
        lg: 'min-h-10 px-[var(--ds-space-5)] py-[var(--ds-space-2x)] text-[length:var(--ds-text-15)]',
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
          'inline-flex min-w-0 items-center justify-center gap-[var(--ds-space-2)] whitespace-normal',
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
