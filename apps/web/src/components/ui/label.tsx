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
        'font-body group-data-[disabled=true]:text-fg-disabled peer-disabled:text-fg-disabled flex items-center gap-2 text-sm leading-none select-none group-data-[disabled=true]:pointer-events-none peer-disabled:cursor-not-allowed',
        className,
      )}
      {...props}
    />
  );
}

export { Label };
