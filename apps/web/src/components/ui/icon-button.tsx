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
        'hover:text-foreground aria-pressed:text-foreground border-border bg-surface-translucent text-fg-2 hover:bg-surface-translucent-selected aria-pressed:bg-surface-hover min-h-0 p-0',
        size === 'sm' && 'size-6',
        size === 'md' && 'size-7',
        size === 'lg' && 'size-8',
        shape === 'circle' ? 'rounded-circle' : 'rounded-sm',
        className,
      )}
      {...props}
    >
      {children}
    </Button>
  );
}

export { IconButton, type IconButtonProps };
