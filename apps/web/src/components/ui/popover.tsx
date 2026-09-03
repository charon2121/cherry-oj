import { Popover as PopoverPrimitive } from '@base-ui/react/popover';
import type { ComponentProps } from 'react';

import { cn } from '@/lib/utils';

type ClassNameProp = { className?: string };
type PopoverSize = 'sm' | 'md';

const popoverSizeClasses: Record<PopoverSize, string> = {
  sm: 'w-80',
  md: 'w-96',
};

const Popover = PopoverPrimitive.Root;
const PopoverTrigger = PopoverPrimitive.Trigger;
const PopoverClose = PopoverPrimitive.Close;

function PopoverPortal(props: PopoverPrimitive.Portal.Props) {
  return <PopoverPrimitive.Portal data-slot="popover-portal" {...props} />;
}

type PopoverContentProps = Omit<PopoverPrimitive.Popup.Props, 'className'> &
  ClassNameProp & {
    align?: PopoverPrimitive.Positioner.Props['align'];
    alignOffset?: PopoverPrimitive.Positioner.Props['alignOffset'];
    collisionPadding?: PopoverPrimitive.Positioner.Props['collisionPadding'];
    side?: PopoverPrimitive.Positioner.Props['side'];
    sideOffset?: PopoverPrimitive.Positioner.Props['sideOffset'];
    size?: PopoverSize;
  };

function PopoverContent({
  align = 'center',
  alignOffset,
  children,
  className,
  collisionPadding = 12,
  side = 'bottom',
  sideOffset = 8,
  size = 'sm',
  ...props
}: PopoverContentProps) {
  return (
    <PopoverPortal>
      <PopoverPrimitive.Positioner
        data-slot="popover-positioner"
        align={align}
        alignOffset={alignOffset}
        collisionPadding={collisionPadding}
        side={side}
        sideOffset={sideOffset}
        className="z-50 max-w-[calc(100vw-var(--ds-space-6))] outline-none"
      >
        <PopoverPrimitive.Popup
          data-slot="popover-content"
          className={cn(
            'text-foreground focus-visible:outline-ring relative grid max-h-[var(--available-height)] max-w-full gap-3 overflow-x-hidden overflow-y-auto rounded-[var(--ds-radius-sm)] border border-[var(--ds-border)] bg-[var(--ds-panel)] p-4 break-words shadow-[var(--ds-elevation-dialog)] transition-opacity duration-[var(--ds-motion-fast)] ease-[var(--ds-ease-standard)] outline-none focus-visible:outline-2 focus-visible:outline-offset-2 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0 motion-reduce:transition-none',
            popoverSizeClasses[size],
            className,
          )}
          {...props}
        >
          {children}
        </PopoverPrimitive.Popup>
      </PopoverPrimitive.Positioner>
    </PopoverPortal>
  );
}

function PopoverHeader({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div data-slot="popover-header" className={cn('grid min-w-0 gap-1', className)} {...props} />
  );
}

function PopoverFooter({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="popover-footer"
      className={cn('flex min-w-0 flex-wrap items-center justify-end gap-2 pt-1', className)}
      {...props}
    />
  );
}

function PopoverTitle({
  className,
  ...props
}: Omit<PopoverPrimitive.Title.Props, 'className'> & ClassNameProp) {
  return (
    <PopoverPrimitive.Title
      data-slot="popover-title"
      className={cn(
        'font-display text-foreground text-sm leading-[var(--ds-leading-heading)] font-[var(--ds-weight-heading)]',
        className,
      )}
      {...props}
    />
  );
}

function PopoverDescription({
  className,
  ...props
}: Omit<PopoverPrimitive.Description.Props, 'className'> & ClassNameProp) {
  return (
    <PopoverPrimitive.Description
      data-slot="popover-description"
      className={cn('text-muted-foreground text-sm leading-[var(--ds-leading-body)]', className)}
      {...props}
    />
  );
}

export {
  Popover,
  PopoverClose,
  PopoverContent,
  PopoverDescription,
  PopoverFooter,
  PopoverHeader,
  PopoverPortal,
  PopoverTitle,
  PopoverTrigger,
};
export type { PopoverContentProps, PopoverSize };
