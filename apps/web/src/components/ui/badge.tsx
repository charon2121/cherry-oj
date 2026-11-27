import { mergeProps } from '@base-ui/react/merge-props';
import { useRender } from '@base-ui/react/use-render';
import { cva, type VariantProps } from 'class-variance-authority';

import { cn } from '@/lib/utils';

const badgeVariants = cva(
  'group/badge inline-flex min-h-5 w-fit max-w-full shrink-0 items-center justify-center gap-1 rounded-md border border-transparent px-2 py-0.5 font-display text-xs leading-tight font-body whitespace-normal break-words transition-[color,background-color,border-color] duration-fast focus-visible:outline-1 focus-visible:outline-offset-0 focus-visible:outline-ring aria-invalid:border-danger-border motion-reduce:transition-none [&>svg]:pointer-events-none [&>svg]:size-3 [&>svg]:shrink-0',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground [a]:hover:bg-brand-surface-hover',
        secondary: 'border-border bg-surface-subtle text-fg-2 [a]:hover:bg-surface-hover',
        destructive: 'bg-danger-solid text-danger-on-solid',
        outline: 'border-border-solid text-foreground [a]:hover:bg-surface-hover',
        ghost: '[a]:hover:bg-surface-hover',
        link: 'text-brand underline underline-offset-4',
        neutral: 'border-border bg-surface-subtle text-fg-2',
        brand: 'border-border bg-brand-soft text-on-brand-soft',
        success: 'border-border bg-success-soft text-success',
        warning: 'border-border bg-warning-soft text-warning',
        danger: 'border-border bg-danger-soft text-danger',
        info: 'border-border bg-info-soft text-info',
        special: 'border-border bg-special-soft text-special',
      },
    },
    defaultVariants: {
      variant: 'neutral',
    },
  },
);

function Badge({
  className,
  variant = 'neutral',
  render,
  ...props
}: useRender.ComponentProps<'span'> & VariantProps<typeof badgeVariants>) {
  return useRender({
    defaultTagName: 'span',
    props: mergeProps<'span'>({ className: cn(badgeVariants({ variant }), className) }, props),
    render,
    state: { slot: 'badge', variant },
  });
}

type BadgeVariant = NonNullable<VariantProps<typeof badgeVariants>['variant']>;

export { Badge, type BadgeVariant, badgeVariants };
