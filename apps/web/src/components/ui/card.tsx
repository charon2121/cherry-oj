import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

function Card({
  className,
  bordered = true,
  elevated = false,
  interactive = false,
  padding,
  radius = 'xl',
  size = 'default',
  ...props
}: ComponentProps<'div'> & {
  bordered?: boolean;
  elevated?: boolean;
  interactive?: boolean;
  padding?: 'sm' | 'md' | 'lg';
  radius?: 'md' | 'lg' | 'xl';
  size?: 'default' | 'sm';
}) {
  const resolvedPadding = padding ?? (size === 'sm' ? 'sm' : 'md');
  return (
    <div
      data-slot="card"
      data-size={size}
      className={cn(
        'group/card text-card-foreground duration-base ease-standard flex min-w-0 flex-col overflow-hidden transition-[background-color,border-color,box-shadow] motion-reduce:transition-none',
        bordered && 'border-border bg-panel shadow-subtle border',
        resolvedPadding === 'sm' && 'gap-4 p-4',
        resolvedPadding === 'md' && 'gap-5 p-6',
        resolvedPadding === 'lg' && 'gap-6 p-8',
        radius === 'md' && 'rounded-md',
        radius === 'lg' && 'rounded-lg',
        radius === 'xl' && 'rounded-xl',
        interactive && 'hover:border-border-strong hover:shadow-raised',
        elevated && 'shadow-raised',
        className,
      )}
      {...props}
    />
  );
}

function CardHeader({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-header"
      className={cn(
        'group/card-header grid min-w-0 auto-rows-min items-start gap-1.5 has-data-[slot=card-action]:grid-cols-[minmax(0,1fr)_auto] has-data-[slot=card-description]:grid-rows-[auto_auto]',
        className,
      )}
      {...props}
    />
  );
}

function CardTitle({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-title"
      className={cn(
        'font-display leading-h2 font-heading tracking-heading text-base group-data-[size=sm]/card:text-sm',
        className,
      )}
      {...props}
    />
  );
}

function CardDescription({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-description"
      className={cn('text-muted-foreground text-sm', className)}
      {...props}
    />
  );
}

function CardAction({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-action"
      className={cn('col-start-2 row-span-2 row-start-1 self-start justify-self-end', className)}
      {...props}
    />
  );
}

function CardContent({ className, ...props }: ComponentProps<'div'>) {
  return <div data-slot="card-content" className={cn('min-w-0', className)} {...props} />;
}

function CardFooter({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-footer"
      className={cn(
        'border-border-soft flex min-w-0 flex-wrap items-center gap-2 border-t pt-4',
        className,
      )}
      {...props}
    />
  );
}

function Panel({
  bordered = true,
  className,
  ...props
}: ComponentProps<'section'> & { bordered?: boolean }) {
  return (
    <section
      data-slot="panel"
      className={cn(
        'min-w-0 py-5',
        bordered && 'border-border bg-panel shadow-subtle rounded-xl border p-5',
        className,
      )}
      {...props}
    />
  );
}

export { Card, CardAction, CardContent, CardDescription, CardFooter, CardHeader, CardTitle, Panel };
