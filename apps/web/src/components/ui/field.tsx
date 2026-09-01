import { cva, type VariantProps } from 'class-variance-authority';
import {
  cloneElement,
  type ComponentProps,
  type ReactElement,
  type ReactNode,
  useId,
  useMemo,
} from 'react';

import { cn } from '@/lib/utils';

import { Label } from './label';

// 骨架取自 shadcn base-nova 官方 field：保留官方的十个子组件、`data-slot` 命名、
// orientation 变体与 `group/field` 选择器体系，因此以后 `shadcn add` 的表单组件能直接插进来。
// 相对官方去掉两类内容：按 color scheme 前缀的主题分支（design-system.md §4 禁止 theme 分支），
// 以及 `opacity-50`、`ring-3 ring-ring/50`、`bg-primary/5` 这类透明度叠加
// （§4 要求 disabled 用专门 token、§7 要求焦点是 2px outline）。
// 官方针对 checkbox/radio 卡片式选择的 `has-data-checked` 样式暂未采用——仓库还没有这类组件。

const fieldVariants = cva('group/field flex w-full min-w-0 data-[invalid=true]:text-danger', {
  variants: {
    orientation: {
      vertical: 'flex-col gap-2 [&>*]:w-full',
      horizontal: 'flex-row items-center gap-3 [&>[data-slot=field-label]]:flex-auto',
      responsive: 'flex-col gap-2 @md/field-group:flex-row @md/field-group:items-center',
    },
  },
  defaultVariants: { orientation: 'vertical' },
});

function FieldSet({ className, ...props }: ComponentProps<'fieldset'>) {
  return (
    <fieldset data-slot="field-set" className={cn('flex flex-col gap-4', className)} {...props} />
  );
}

function FieldLegend({
  className,
  variant = 'legend',
  ...props
}: ComponentProps<'legend'> & { variant?: 'legend' | 'label' }) {
  return (
    <legend
      data-slot="field-legend"
      data-variant={variant}
      className={cn(
        'mb-1.5 font-[var(--ds-weight-medium)] data-[variant=label]:text-[length:var(--ds-text-sm)] data-[variant=legend]:text-[length:var(--ds-text-base)]',
        className,
      )}
      {...props}
    />
  );
}

function FieldGroup({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="field-group"
      className={cn(
        'group/field-group @container/field-group flex w-full flex-col gap-5',
        className,
      )}
      {...props}
    />
  );
}

function Field({
  className,
  orientation = 'vertical',
  ...props
}: ComponentProps<'div'> & VariantProps<typeof fieldVariants>) {
  return (
    <div
      role="group"
      data-slot="field"
      data-orientation={orientation}
      className={cn(fieldVariants({ orientation }), className)}
      {...props}
    />
  );
}

function FieldContent({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="field-content"
      className={cn('group/field-content flex flex-1 flex-col gap-0.5 leading-snug', className)}
      {...props}
    />
  );
}

function FieldLabel({ className, ...props }: ComponentProps<typeof Label>) {
  return (
    <Label
      data-slot="field-label"
      className={cn(
        'group/field-label peer/field-label flex w-fit gap-2 leading-snug group-data-[disabled=true]/field:text-[var(--ds-fg-disabled)]',
        className,
      )}
      {...props}
    />
  );
}

function FieldTitle({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="field-label"
      className={cn(
        'flex w-fit items-center gap-2 text-[length:var(--ds-text-sm)] font-[var(--ds-weight-medium)] group-data-[disabled=true]/field:text-[var(--ds-fg-disabled)]',
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
      className={cn(
        'text-muted-foreground text-left text-[length:var(--ds-text-sm)] leading-normal wrap-anywhere last:mt-0 [&>a]:underline [&>a]:underline-offset-4',
        className,
      )}
      {...props}
    />
  );
}

function FieldSeparator({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="field-separator"
      className={cn('border-border -my-2 h-5 border-t', className)}
      {...props}
    />
  );
}

function FieldError({
  className,
  children,
  errors,
  ...props
}: ComponentProps<'p'> & { errors?: Array<{ message?: string } | undefined> }) {
  const content = useMemo(() => {
    if (children) return children;
    if (!errors?.length) return null;
    const unique = [...new Map(errors.map((error) => [error?.message, error])).values()];
    if (unique.length === 1) return unique[0]?.message;
    return (
      <ul className="ml-4 flex list-disc flex-col gap-1">
        {unique.map((error, index) => error?.message && <li key={index}>{error.message}</li>)}
      </ul>
    );
  }, [children, errors]);

  if (!content) return null;

  return (
    <p
      role="alert"
      data-slot="field-error"
      className={cn('text-danger text-[length:var(--ds-text-sm)] wrap-anywhere', className)}
      {...props}
    >
      {content}
    </p>
  );
}

type FieldControlProps = Readonly<{
  id?: string;
  disabled?: boolean;
  required?: boolean;
  'aria-describedby'?: string;
  'aria-invalid'?: ComponentProps<'input'>['aria-invalid'];
}>;

export type FormFieldProps = Omit<ComponentProps<'div'>, 'children'> &
  Readonly<{
    children: ReactElement<FieldControlProps>;
    description?: ReactNode;
    error?: ReactNode;
    invalid?: boolean;
    label: ReactNode;
    required?: boolean;
    disabled?: boolean;
  }>;

function hasContent(value: ReactNode): boolean {
  return value !== undefined && value !== null && value !== false && value !== '';
}

function mergeIds(...ids: Array<string | undefined>): string | undefined {
  const present = ids.filter((id): id is string => Boolean(id));
  return present.length === 0 ? undefined : present.join(' ');
}

/**
 * 表单字段的自动接线组合件。
 *
 * 官方 `Field` 只是 `role="group"` 的排版容器，`aria-describedby` / `aria-invalid` 的关联在
 * 官方那边由绑定 react-hook-form 的 `FormField` 承担。本仓库用 TanStack Form，所以这里用
 * `useId` + `cloneElement` 自己完成同样的关联——表单库无关，调用处不必手工接线。
 * 命名沿用 shadcn 的分工：`Field` 管排版，`FormField` 管接线。
 */
function FormField({
  children,
  className,
  description,
  disabled = false,
  error,
  invalid = false,
  label,
  required = false,
  ...props
}: FormFieldProps) {
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
    <Field
      data-disabled={disabled ? '' : undefined}
      data-invalid={isInvalid ? '' : undefined}
      className={className}
      {...props}
    >
      <FieldLabel htmlFor={controlId}>
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </FieldLabel>
      {control}
      {hasDescription ? (
        <FieldDescription id={descriptionId}>{description}</FieldDescription>
      ) : null}
      {hasError ? <FieldError id={errorId}>{error}</FieldError> : null}
    </Field>
  );
}

export {
  Field,
  FieldContent,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSeparator,
  FieldSet,
  FieldTitle,
  fieldVariants,
  FormField,
};
