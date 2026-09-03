import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

// 骨架取自 shadcn base-nova 官方 label。官方用 `opacity-50` 表达禁用，
// design-system.md §4 要求 disabled 使用专门 token，因此改为 --ds-fg-disabled。
function Label({ className, ...props }: ComponentProps<'label'>) {
  return (
    // 关联由调用方提供：FormField 与 SelectField 都会传 htmlFor，规则无法跨组件看到。
    // eslint-disable-next-line jsx-a11y/label-has-associated-control -- 通用 label primitive
    <label
      data-slot="label"
      className={cn(
        'flex items-center gap-2 text-[length:var(--ds-text-sm)] leading-none font-[var(--ds-weight-body)] select-none group-data-[disabled=true]:pointer-events-none group-data-[disabled=true]:text-[var(--ds-fg-disabled)] peer-disabled:cursor-not-allowed peer-disabled:text-[var(--ds-fg-disabled)]',
        className,
      )}
      {...props}
    />
  );
}

export { Label };
