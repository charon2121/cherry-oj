import { type ReactNode } from 'react';

import { cn } from '@/lib/utils';

import { Button, type ButtonProps } from './button';

type IconButtonProps = Omit<
  ButtonProps,
  'aria-label' | 'children' | 'loading' | 'loadingLabel' | 'size' | 'variant'
> &
  Readonly<{
    children: ReactNode;
    label: string;
    size?: 'sm' | 'md';
    variant?: 'secondary' | 'ghost' | 'danger';
  }>;

function IconButton({
  children,
  className,
  label,
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
      className={cn(size === 'sm' ? 'size-8 min-h-0 p-0' : 'size-10 min-h-0 p-0', className)}
      {...props}
    >
      {children}
    </Button>
  );
}

export { IconButton, type IconButtonProps };
