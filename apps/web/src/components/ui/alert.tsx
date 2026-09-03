import { cva, type VariantProps } from 'class-variance-authority';
import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

// 骨架取自 shadcn base-nova 官方 alert：保留官方的四个子组件、`has-[>svg]` 网格布局
// 与 `data-slot` 命名。相对官方只改颜色相关 class：官方用 `text-destructive/90`
// 透明度叠加承担次级前景，design-system.md §4 禁止；改为语义 token。
//
// 官方只有 default / destructive 两个变体，也没有 OJ 需要的五类状态语义与
// aria-live 控制——那部分由同目录的 InlineNotice 组合本文件的子组件实现。
const alertVariants = cva(
  'group/alert relative grid w-full min-w-0 gap-0.5 rounded-md border px-3 py-2.5 text-left text-sm has-data-[slot=alert-action]:pr-18 has-[>svg]:grid-cols-[auto_1fr] has-[>svg]:gap-x-2 *:[svg]:row-span-2 *:[svg]:self-start *:[svg]:text-current *:[svg:not([class*=size-])]:size-4',
  {
    variants: {
      variant: {
        default: 'border-border bg-card text-card-foreground',
        destructive: 'border-danger-border bg-card text-danger',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
);

function Alert({
  className,
  variant,
  ...props
}: ComponentProps<'div'> & VariantProps<typeof alertVariants>) {
  return (
    <div
      data-slot="alert"
      role="alert"
      className={cn(alertVariants({ variant }), className)}
      {...props}
    />
  );
}

function AlertTitle({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="alert-title"
      className={cn(
        'font-heading group-has-[>svg]/alert:col-start-2 [&_a]:underline [&_a]:underline-offset-[3px]',
        className,
      )}
      {...props}
    />
  );
}

function AlertDescription({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      data-slot="alert-description"
      className={cn(
        'text-muted-foreground text-sm wrap-anywhere [&_a]:underline [&_a]:underline-offset-[3px] [&_p:not(:last-child)]:mb-4',
        className,
      )}
      {...props}
    />
  );
}

function AlertAction({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div data-slot="alert-action" className={cn('absolute top-2 right-2', className)} {...props} />
  );
}

export { Alert, AlertAction, AlertDescription, AlertTitle, alertVariants };
