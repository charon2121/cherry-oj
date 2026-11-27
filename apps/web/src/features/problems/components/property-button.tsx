import type { ComponentProps, ReactNode } from 'react';

import { cn } from '@/lib/utils';

type PropertyButtonProps = Omit<ComponentProps<'button'>, 'children'> &
  Readonly<{
    icon?: ReactNode;
    children?: ReactNode;
    /** 还没有值时（例如"添加标签"）用灰字。 */
    muted?: boolean;
  }>;

// 右侧属性卡片内的紧凑动作按钮：圆角矩形轮廓、清晰悬停反馈与键盘焦点指示。
function PropertyButton({
  icon,
  children,
  muted = false,
  className,
  type = 'button',
  ...props
}: PropertyButtonProps) {
  return (
    <button
      type={type}
      data-slot="property-button"
      className={cn(
        'font-body hover:bg-surface-hover focus-visible:outline-ring data-[popup-open]:bg-surface-hover duration-fast ease-standard inline-flex h-8 max-w-full min-w-0 items-center gap-2 rounded-lg px-2.5 text-sm transition-colors outline-none focus-visible:outline-1 disabled:cursor-default disabled:hover:bg-transparent motion-reduce:transition-none [&_svg]:size-4 [&_svg]:shrink-0',
        muted ? 'text-fg-meta hover:text-fg-2' : 'text-fg-2',
        className,
      )}
      {...props}
    >
      {icon === undefined ? null : (
        <span className="text-fg-meta inline-flex size-4 items-center justify-center">{icon}</span>
      )}
      <span className="min-w-0 truncate">{children}</span>
    </button>
  );
}

export { PropertyButton, type PropertyButtonProps };
