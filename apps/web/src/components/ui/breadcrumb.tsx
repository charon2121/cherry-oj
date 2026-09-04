import { mergeProps } from '@base-ui/react/merge-props';
import { useRender } from '@base-ui/react/use-render';
import { ChevronRight } from 'lucide-react';
import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

// 骨架取自 shadcn base-nova 官方 breadcrumb（registry 返回 200）。保留官方的七个子组件、
// data-slot、useRender 渲染模式与可访问性行为（nav[aria-label] + ol/li + aria-current）。
// 相对官方只改两处：
//   - 分隔符从官方的 IconPlaceholder 换成 Lucide ChevronRight，本仓库图标只有一个来源；
//   - 尺寸对齐工具条：文字 text-cap、图标 14px——面包屑是导航痕迹不是标题，不该抢标题的亮度。
function Breadcrumb({ className, ...props }: ComponentProps<'nav'>) {
  return <nav aria-label="面包屑" data-slot="breadcrumb" className={cn(className)} {...props} />;
}

function BreadcrumbList({ className, ...props }: ComponentProps<'ol'>) {
  return (
    <ol
      data-slot="breadcrumb-list"
      className={cn(
        'text-fg-meta text-cap flex flex-wrap items-center gap-1 wrap-break-word',
        className,
      )}
      {...props}
    />
  );
}

function BreadcrumbItem({ className, ...props }: ComponentProps<'li'>) {
  return (
    <li
      data-slot="breadcrumb-item"
      className={cn('inline-flex items-center gap-1', className)}
      {...props}
    />
  );
}

function BreadcrumbLink({ className, render, ...props }: useRender.ComponentProps<'a'>) {
  return useRender({
    defaultTagName: 'a',
    props: mergeProps<'a'>(
      {
        className: cn(
          'hover:text-foreground focus-visible:outline-ring rounded-xs no-underline transition-colors duration-fast ease-standard focus-visible:outline-1 focus-visible:outline-offset-0 motion-reduce:transition-none',
          className,
        ),
      },
      props,
    ),
    render,
    state: { slot: 'breadcrumb-link' },
  });
}

// 末项是当前位置：它是这一串里唯一提亮的一段，其余保持 fg-meta（原则 C）。
//
// 相对官方骨架去掉了 `role="link" aria-disabled="true"`。官方那样写会在无障碍树里留下一个
// 点不动的假链接——当侧栏已有同名导航项时，`getByRole('link', { name })` 会同时命中两个，
// 屏幕阅读器用户听到的也是两个"链接"。当前页不是链接，`aria-current="page"` 放在普通文本上
// 完全有效，因此这里只保留后者。这是对官方实现的有意偏离，不是遗漏。
function BreadcrumbPage({ className, ...props }: ComponentProps<'span'>) {
  return (
    <span
      data-slot="breadcrumb-page"
      aria-current="page"
      className={cn('text-foreground font-regular', className)}
      {...props}
    />
  );
}

function BreadcrumbSeparator({ children, className, ...props }: ComponentProps<'li'>) {
  return (
    <li
      data-slot="breadcrumb-separator"
      role="presentation"
      aria-hidden="true"
      className={cn('[&>svg]:size-3.5', className)}
      {...props}
    >
      {children ?? <ChevronRight />}
    </li>
  );
}

function BreadcrumbEllipsis({ className, ...props }: ComponentProps<'span'>) {
  return (
    <span
      data-slot="breadcrumb-ellipsis"
      role="presentation"
      aria-hidden="true"
      className={cn('flex size-4 items-center justify-center', className)}
      {...props}
    >
      …<span className="sr-only">更多</span>
    </span>
  );
}

export {
  Breadcrumb,
  BreadcrumbEllipsis,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
};
