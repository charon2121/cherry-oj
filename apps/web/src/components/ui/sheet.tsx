import { Dialog as SheetPrimitive } from '@base-ui/react/dialog';
import { X } from 'lucide-react';
import type { ComponentProps } from 'react';

import { cn } from '@/lib/utils';

const Sheet = SheetPrimitive.Root;
const SheetTrigger = SheetPrimitive.Trigger;
const SheetClose = SheetPrimitive.Close;

function SheetPortal(props: SheetPrimitive.Portal.Props) {
  return <SheetPrimitive.Portal data-slot="sheet-portal" {...props} />;
}

function SheetBackdrop({ className, ...props }: SheetPrimitive.Backdrop.Props) {
  return (
    <SheetPrimitive.Backdrop
      data-slot="sheet-backdrop"
      className={cn(
        'bg-overlay duration-fast ease-standard fixed inset-0 z-50 min-h-dvh transition-opacity data-[ending-style]:opacity-0 data-[starting-style]:opacity-0 motion-reduce:transition-none',
        className,
      )}
      {...props}
    />
  );
}

function SheetViewport({ className, ...props }: SheetPrimitive.Viewport.Props) {
  return (
    <SheetPrimitive.Viewport
      data-slot="sheet-viewport"
      className={cn('pointer-events-none fixed inset-0 z-50 min-h-dvh', className)}
      {...props}
    />
  );
}

type SheetContentProps = SheetPrimitive.Popup.Props &
  Readonly<{
    closeLabel?: string;
    side?: 'left' | 'right';
  }>;

function SheetContent({
  children,
  className,
  closeLabel = '关闭导航',
  side = 'left',
  ...props
}: SheetContentProps) {
  return (
    <SheetPortal>
      <SheetBackdrop />
      <SheetViewport>
        <SheetPrimitive.Popup
          data-slot="sheet-content"
          data-side={side}
          className={cn(
            'text-sidebar-foreground border-border bg-panel shadow-dialog duration-fast ease-standard pointer-events-auto fixed inset-y-0 flex w-[min(18rem,calc(100vw-var(--space-8)))] flex-col border-r transition-opacity outline-none data-[ending-style]:opacity-0 data-[starting-style]:opacity-0 motion-reduce:transition-none',
            side === 'left' ? 'left-0' : 'right-0 border-r-0 border-l',
            className,
          )}
          {...props}
        >
          {children}
          <SheetPrimitive.Close
            data-slot="sheet-close"
            aria-label={closeLabel}
            className="border-border-strong bg-surface-subtle text-foreground hover:bg-accent focus-visible:outline-ring duration-fast absolute top-3 right-3 inline-flex size-10 items-center justify-center rounded-sm border transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 motion-reduce:transition-none"
          >
            <X className="size-4" aria-hidden="true" />
          </SheetPrimitive.Close>
        </SheetPrimitive.Popup>
      </SheetViewport>
    </SheetPortal>
  );
}

function SheetHeader({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="sheet-header"
      className={cn('border-sidebar-border grid min-w-0 gap-1 border-b p-4 pr-14', className)}
      {...props}
    />
  );
}

function SheetTitle({ className, ...props }: SheetPrimitive.Title.Props) {
  return (
    <SheetPrimitive.Title
      data-slot="sheet-title"
      className={cn(
        'font-display text-foreground leading-heading font-heading text-base',
        className,
      )}
      {...props}
    />
  );
}

function SheetDescription({ className, ...props }: SheetPrimitive.Description.Props) {
  return (
    <SheetPrimitive.Description
      data-slot="sheet-description"
      className={cn('text-muted-foreground text-sm', className)}
      {...props}
    />
  );
}

export {
  Sheet,
  SheetBackdrop,
  SheetClose,
  SheetContent,
  type SheetContentProps,
  SheetDescription,
  SheetHeader,
  SheetPortal,
  SheetTitle,
  SheetTrigger,
  SheetViewport,
};
