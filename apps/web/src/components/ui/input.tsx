import { Input as InputPrimitive } from '@base-ui/react/input';
import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

import { controlClasses } from './control-classes';

// 骨架取自 shadcn base-nova 官方 input：走 Base UI 的 Input primitive。
// 外观 class 换成本仓库共享的 controlClasses，理由见该文件注释。
function Input({ className, type, ...props }: ComponentProps<'input'>) {
  return (
    <InputPrimitive
      type={type}
      data-slot="input"
      className={cn(
        controlClasses,
        'file:text-foreground file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-[length:var(--ds-text-sm)] file:font-[var(--ds-weight-body)]',
        className,
      )}
      {...props}
    />
  );
}

export { Input };
