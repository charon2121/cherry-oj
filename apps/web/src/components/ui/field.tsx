import { cloneElement, type ComponentProps, type ReactElement, type ReactNode, useId } from 'react';

import { cn } from '@/lib/utils';

const controlClasses =
  'min-h-10 w-full rounded-sm border border-border-strong bg-input-background px-3 py-2 text-foreground transition-colors duration-[var(--ds-motion-fast)] placeholder:text-muted-foreground hover:bg-accent focus-visible:border-ring focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:cursor-not-allowed disabled:border-border-strong disabled:bg-secondary disabled:text-[var(--ds-fg-disabled)] aria-invalid:border-[var(--ds-danger-border)]';

type FieldControlProps = Readonly<{
  id?: string;
  disabled?: boolean;
  required?: boolean;
  'aria-describedby'?: string;
  'aria-invalid'?: ComponentProps<'input'>['aria-invalid'];
}>;

type FieldProps = Omit<ComponentProps<'div'>, 'children'> &
  Readonly<{
    children: ReactElement<FieldControlProps>;
    description?: ReactNode;
    disabled?: boolean;
    error?: ReactNode;
    invalid?: boolean;
    label: ReactNode;
    required?: boolean;
  }>;

function hasContent(value: ReactNode): boolean {
  return value !== null && value !== undefined && value !== false;
}

function mergeIds(...ids: Array<string | undefined>): string | undefined {
  const result = ids.filter((id): id is string => id !== undefined && id.length > 0).join(' ');
  return result.length > 0 ? result : undefined;
}

type LabelProps = ComponentProps<'label'> & Readonly<{ htmlFor: string }>;

function Label({ className, htmlFor, ...props }: LabelProps) {
  return (
    <label
      htmlFor={htmlFor}
      data-slot="field-label"
      className={cn(
        'font-display text-[length:var(--ds-text-sm)] font-[var(--ds-weight-body)] text-[var(--ds-fg-2)]',
        className,
      )}
      {...props}
    />
  );
}

function FieldDescription({ className, ...props }: ComponentProps<'p'>) {
  return (
    <p
      data-slot="field-description"
      className={cn('text-[length:var(--ds-text-sm)] text-[var(--ds-fg-meta)]', className)}
      {...props}
    />
  );
}

function FieldError({ className, ...props }: ComponentProps<'p'>) {
  return (
    <p
      data-slot="field-error"
      className={cn('text-danger text-[length:var(--ds-text-sm)]', className)}
      {...props}
    />
  );
}

function Field({
  children,
  className,
  description,
  disabled = false,
  error,
  invalid = false,
  label,
  required = false,
  ...props
}: FieldProps) {
  const generatedId = useId();
  const controlId = children.props.id ?? `field-${generatedId}`;
  const descriptionId = `${controlId}-description`;
  const errorId = `${controlId}-error`;
  const hasDescription = hasContent(description);
  const hasError = hasContent(error);
  const childInvalid =
    children.props['aria-invalid'] === true || children.props['aria-invalid'] === 'true';
  const isInvalid = invalid || hasError || childInvalid;
  const describedBy = mergeIds(
    children.props['aria-describedby'],
    hasDescription ? descriptionId : undefined,
    hasError ? errorId : undefined,
  );

  const control = cloneElement(children, {
    id: controlId,
    ...(disabled || children.props.disabled ? { disabled: true } : {}),
    ...(required || children.props.required ? { required: true } : {}),
    ...(describedBy === undefined ? {} : { 'aria-describedby': describedBy }),
    ...(isInvalid ? { 'aria-invalid': true } : {}),
  });

  return (
    <div
      data-slot="field"
      data-disabled={disabled ? '' : undefined}
      data-invalid={isInvalid ? '' : undefined}
      className={cn('grid min-w-0 gap-2', className)}
      {...props}
    >
      <Label htmlFor={controlId}>
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </Label>
      {control}
      {hasDescription ? (
        <FieldDescription id={descriptionId}>{description}</FieldDescription>
      ) : null}
      {hasError ? <FieldError id={errorId}>{error}</FieldError> : null}
    </div>
  );
}

function Input({ className, type = 'text', ...props }: ComponentProps<'input'>) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        controlClasses,
        'file:font-inherit file:border-0 file:bg-transparent',
        className,
      )}
      {...props}
    />
  );
}

function Textarea({ className, ...props }: ComponentProps<'textarea'>) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(controlClasses, 'min-h-24 resize-y', className)}
      {...props}
    />
  );
}

function Select({ className, ...props }: ComponentProps<'select'>) {
  return (
    <select
      data-slot="select"
      className={cn(controlClasses, 'cursor-pointer', className)}
      {...props}
    />
  );
}

export { Field, FieldDescription, FieldError, type FieldProps, Input, Label, Select, Textarea };
