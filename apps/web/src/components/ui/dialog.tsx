import { Dialog as DialogPrimitive } from '@base-ui/react/dialog';
import { X } from 'lucide-react';
import type { ComponentProps } from 'react';

import { cn } from '@/lib/utils';

type ClassNameProp = { className?: string };
type DialogSize = 'sm' | 'md';

const dialogSizeClasses: Record<DialogSize, string> = {
  sm: 'max-w-sm',
  md: 'max-w-lg',
};

const Dialog = DialogPrimitive.Root;
const DialogTrigger = DialogPrimitive.Trigger;
const DialogClose = DialogPrimitive.Close;

function DialogPortal(props: DialogPrimitive.Portal.Props) {
  return <DialogPrimitive.Portal data-slot="dialog-portal" {...props} />;
}

function DialogBackdrop({
  className,
  ...props
}: Omit<DialogPrimitive.Backdrop.Props, 'className'> & ClassNameProp) {
  return (
    <DialogPrimitive.Backdrop
      data-slot="dialog-backdrop"
      className={cn(
        'bg-overlay duration-fast ease-standard fixed inset-0 z-50 min-h-dvh transition-opacity data-[ending-style]:opacity-0 data-[starting-style]:opacity-0 supports-[-webkit-touch-callout:none]:absolute motion-reduce:transition-none',
        className,
      )}
      {...props}
    />
  );
}

function DialogViewport({
  className,
  ...props
}: Omit<DialogPrimitive.Viewport.Props, 'className'> & ClassNameProp) {
  return (
    <DialogPrimitive.Viewport
      data-slot="dialog-viewport"
      className={cn(
        'pointer-events-none fixed inset-0 z-50 grid min-h-dvh place-items-center overflow-y-auto p-3 sm:p-6',
        className,
      )}
      {...props}
    />
  );
}

type DialogContentProps = Omit<DialogPrimitive.Popup.Props, 'className'> &
  ClassNameProp & {
    closeLabel?: string;
    size?: DialogSize;
  };

function DialogContent({
  children,
  className,
  closeLabel = '关闭对话框',
  size = 'md',
  ...props
}: DialogContentProps) {
  return (
    <DialogPortal>
      <DialogBackdrop />
      <DialogViewport>
        <DialogPrimitive.Popup
          data-slot="dialog-content"
          className={cn(
            'text-foreground focus-visible:outline-ring border-border bg-panel shadow-dialog duration-fast ease-standard pointer-events-auto relative grid max-h-[calc(100dvh-var(--space-6))] w-full gap-4 overflow-x-hidden overflow-y-auto rounded-lg border p-5 break-words transition-opacity outline-none focus-visible:outline-2 focus-visible:outline-offset-2 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0 motion-reduce:transition-none sm:p-6',
            dialogSizeClasses[size],
            className,
          )}
          {...props}
        >
          {children}
          <DialogPrimitive.Close
            data-slot="dialog-close"
            className="border-border-strong bg-surface-subtle text-foreground hover:bg-accent focus-visible:outline-ring duration-fast ease-standard disabled:text-fg-disabled absolute top-3 right-3 inline-flex size-10 items-center justify-center rounded-sm border transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 disabled:pointer-events-none disabled:cursor-not-allowed motion-reduce:transition-none"
            aria-label={closeLabel}
          >
            <X className="size-4" aria-hidden="true" />
          </DialogPrimitive.Close>
        </DialogPrimitive.Popup>
      </DialogViewport>
    </DialogPortal>
  );
}

function DialogHeader({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="dialog-header"
      className={cn('grid min-w-0 gap-2 pr-10 text-left', className)}
      {...props}
    />
  );
}

function DialogFooter({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="dialog-footer"
      className={cn('flex min-w-0 flex-col gap-2 pt-2 sm:flex-row sm:justify-end', className)}
      {...props}
    />
  );
}

function DialogTitle({
  className,
  ...props
}: Omit<DialogPrimitive.Title.Props, 'className'> & ClassNameProp) {
  return (
    <DialogPrimitive.Title
      data-slot="dialog-title"
      className={cn('font-display text-foreground leading-heading font-heading text-lg', className)}
      {...props}
    />
  );
}

function DialogDescription({
  className,
  ...props
}: Omit<DialogPrimitive.Description.Props, 'className'> & ClassNameProp) {
  return (
    <DialogPrimitive.Description
      data-slot="dialog-description"
      className={cn('text-muted-foreground leading-body text-sm', className)}
      {...props}
    />
  );
}

export {
  Dialog,
  DialogBackdrop,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogPortal,
  DialogTitle,
  DialogTrigger,
  DialogViewport,
};
export type { DialogContentProps, DialogSize };
