import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

import { controlClasses } from './control-classes';

// 骨架取自 shadcn base-nova 官方 textarea，保留官方的 field-sizing-content 自动高度。
function Textarea({ className, ...props }: ComponentProps<'textarea'>) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(controlClasses, 'flex field-sizing-content min-h-16', className)}
      {...props}
    />
  );
}

export { Textarea };
