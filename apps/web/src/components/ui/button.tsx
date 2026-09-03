import { Button as ButtonPrimitive } from '@base-ui/react/button';
import { cva, type VariantProps } from 'class-variance-authority';
import { LoaderCircle } from 'lucide-react';

import { cn } from '@/lib/utils';

const secondaryButtonClasses =
  'bg-surface-translucent-hover text-fg-2 hover:bg-surface-translucent-selected active:bg-surface-translucent aria-pressed:bg-surface-translucent-selected';

const buttonVariants = cva(
  'relative inline-flex max-w-full shrink-0 cursor-pointer items-center justify-center gap-2 rounded-sm border border-transparent font-display leading-tight font-body transition-[background-color,color,border-color] duration-fast ease-standard select-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:pointer-events-none disabled:cursor-not-allowed disabled:border-border! disabled:bg-surface-translucent! disabled:text-fg-disabled! motion-reduce:transition-none [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*="size-"])]:size-4',
  {
    variants: {
      variant: {
        primary:
          'bg-primary text-primary-foreground hover:bg-brand-surface-hover active:bg-brand-surface-active aria-pressed:bg-brand-surface-active disabled:bg-secondary disabled:text-fg-disabled',
        secondary: secondaryButtonClasses,
        ghost:
          'border-border-solid bg-surface-translucent text-fg-ghost hover:bg-surface-translucent-selected active:bg-surface-translucent aria-pressed:bg-surface-translucent-selected aria-pressed:text-foreground',
        subtle: secondaryButtonClasses,
        toolbar:
          'rounded-micro border-border-soft bg-surface-translucent-selected text-fg-meta shadow-subtle hover:bg-surface-hover hover:text-fg-2 active:bg-surface-translucent-selected aria-pressed:text-foreground',
        danger:
          'bg-destructive text-destructive-foreground hover:bg-danger active:bg-danger-solid aria-pressed:outline-2 aria-pressed:outline-offset-[-2px] aria-pressed:outline-danger-on-solid',
      },
      size: {
        sm: 'h-6 px-1x text-xs',
        md: 'min-h-8 px-4 py-2 text-sm',
        lg: 'min-h-10 px-5 py-2x text-15',
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
