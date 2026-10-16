import type { ComponentProps, ReactNode } from 'react';

import { cn } from '@/lib/utils';

type PropertyButtonProps = Omit<ComponentProps<'button'>, 'children'> &
  Readonly<{
    icon?: ReactNode;
    children?: ReactNode;
    /** 还没有值时（例如"添加标签"）用灰字。 */
    muted?: boolean;
  }>;

// linear.app 详情页右栏的属性值：高 28px 的胶囊按钮，内边距 0 10 0 6，13px/500，静止时没有底色，
// 悬停才出现底色；点开即在原地修改（docs/design-system/measurements.md「详情页」）。
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
        'font-body hover:bg-surface-hover focus-visible:outline-ring data-[popup-open]:bg-surface-hover duration-fast ease-standard inline-flex h-7 max-w-full min-w-0 items-center gap-2 rounded-full pr-2.5 pl-1.5 text-sm transition-colors outline-none focus-visible:outline-1 disabled:cursor-default disabled:hover:bg-transparent motion-reduce:transition-none [&_svg]:size-3.5 [&_svg]:shrink-0',
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
