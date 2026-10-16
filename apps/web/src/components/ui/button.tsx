import { Button as ButtonPrimitive } from '@base-ui/react/button';
import { cva, type VariantProps } from 'class-variance-authority';
import { LoaderCircle } from 'lucide-react';

import { cn } from '@/lib/utils';

// 按钮取自 linear.app 实测：全部是胶囊；小号 28px/12px（工具条、对话框），中号 32px/13px。
// 次要按钮是比面板亮一档的底；浅色主题下再加 0.5px 环与极轻阴影（shadow-ring），暗色下没有。
const secondaryButtonClasses =
  'bg-surface text-fg-2 shadow-ring hover:bg-surface-hover hover:text-foreground active:bg-surface-hover aria-pressed:bg-surface-hover aria-pressed:text-foreground';

const buttonVariants = cva(
  'relative inline-flex max-w-full shrink-0 cursor-pointer items-center justify-center gap-1x rounded-full border-hairline border-transparent font-display leading-tight font-body transition-[background-color,color,border-color] duration-fast ease-standard select-none focus-visible:outline-1 focus-visible:outline-offset-0 focus-visible:outline-ring disabled:pointer-events-none disabled:cursor-not-allowed disabled:border-border! disabled:bg-surface-translucent! disabled:text-fg-disabled! motion-reduce:transition-none [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*="size-"])]:size-4',
  {
    variants: {
      variant: {
        primary:
          'bg-primary text-primary-foreground shadow-subtle hover:bg-brand-surface-hover active:bg-brand-surface-active aria-pressed:bg-brand-surface-active disabled:bg-secondary disabled:text-fg-disabled',
        secondary: secondaryButtonClasses,
        ghost:
          'bg-transparent text-fg-2 hover:bg-surface-translucent-hover hover:text-foreground active:bg-surface-translucent-selected aria-pressed:bg-surface-hover aria-pressed:text-foreground',
        subtle: secondaryButtonClasses,
        toolbar:
          'bg-surface text-fg-2 shadow-ring hover:bg-surface-hover hover:text-foreground active:bg-surface-hover aria-pressed:bg-surface-hover aria-pressed:text-foreground',
        danger:
          'bg-destructive text-destructive-foreground hover:bg-danger active:bg-danger-solid aria-pressed:outline-2 aria-pressed:outline-offset-[-2px] aria-pressed:outline-danger-on-solid',
      },
      size: {
        sm: 'h-7 px-[10px] text-xs',
        md: 'min-h-8 px-3 py-1x text-sm',
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
