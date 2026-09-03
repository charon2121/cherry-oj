import { type ReactNode } from 'react';

import { cn } from '@/lib/utils';

import { Button, type ButtonProps } from './button';

type IconButtonProps = Omit<
  ButtonProps,
  'aria-label' | 'children' | 'loading' | 'loadingLabel' | 'size' | 'variant'
> &
  Readonly<{
    active?: boolean;
    children: ReactNode;
    label: string;
    shape?: 'circle' | 'square';
    size?: 'sm' | 'md' | 'lg';
    variant?: 'secondary' | 'ghost' | 'danger';
  }>;

function IconButton({
  active = false,
  children,
  className,
  label,
  shape = 'circle',
  size = 'md',
  title,
  variant = 'ghost',
  ...props
}: IconButtonProps) {
  return (
    <Button
      aria-label={label}
      title={title ?? label}
      variant={variant}
      size={size}
      aria-pressed={active || undefined}
      className={cn(
        'hover:text-foreground aria-pressed:text-foreground min-h-0 border-[var(--ds-border)] bg-[var(--ds-surface-translucent)] p-0 text-[var(--ds-fg-2)] hover:bg-[var(--ds-surface-translucent-selected)] aria-pressed:bg-[var(--ds-surface-hover)]',
        size === 'sm' && 'size-6',
        size === 'md' && 'size-7',
        size === 'lg' && 'size-8',
        shape === 'circle' ? 'rounded-[var(--ds-radius-circle)]' : 'rounded-[var(--ds-radius-sm)]',
        className,
      )}
      {...props}
    >
      {children}
    </Button>
  );
}

export { IconButton, type IconButtonProps };
