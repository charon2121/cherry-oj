import { mergeProps } from '@base-ui/react/merge-props';
import { useRender } from '@base-ui/react/use-render';
import { cva, type VariantProps } from 'class-variance-authority';

import { cn } from '@/lib/utils';

// 骨架取自 shadcn base-nova 官方 badge：useRender + mergeProps 让徽章能渲染成
// <a>、<button> 或任意元素，属性合并与 ref 转发由 Base UI 处理。
// 相对官方只改三处，且都有设计系统条款支撑：
//   1. 颜色全部换成本仓库语义 token，不使用 /10 /20 这类透明度叠加（design-system.md §4）；
//   2. 焦点用 2px outline + offset，不用官方的透明光晕 ring（§7）；
//   3. 尺寸放宽为 min-h-6 + 可换行，官方的 h-5 + whitespace-nowrap 会裁切长中文（§7）。
const badgeVariants = cva(
  'group/badge inline-flex min-h-[17px] w-fit max-w-full shrink-0 items-center justify-center gap-[var(--ds-space-1)] rounded-[var(--ds-radius-micro)] border border-transparent px-[var(--ds-space-2)] py-px font-display text-[length:var(--ds-text-tiny)] leading-[1.5] font-[var(--ds-weight-body)] tracking-[0.02em] whitespace-normal break-words transition-[color,background-color,border-color] duration-[var(--ds-motion-fast)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring aria-invalid:border-[var(--ds-danger-border)] motion-reduce:transition-none [&>svg]:pointer-events-none [&>svg]:size-3 [&>svg]:shrink-0',
  {
    variants: {
      variant: {
        // 官方变体，语义按 design-system.md §4 的一次性映射
        default: 'bg-primary text-primary-foreground [a]:hover:bg-[var(--ds-brand-surface-hover)]',
        secondary:
          'border-[var(--ds-border-soft)] bg-[var(--ds-surface-translucent-selected)] text-foreground [a]:hover:bg-[var(--ds-surface-hover)]',
        destructive: 'bg-danger-solid text-danger-on-solid',
        outline: 'border-border text-foreground [a]:hover:bg-[var(--ds-surface-translucent-hover)]',
        ghost: '[a]:hover:bg-[var(--ds-surface-translucent-hover)]',
        link: 'text-brand underline underline-offset-4',
        // Cherry OJ 语义扩展：品牌软底与五类状态，均为 soft surface + 状态边界
        neutral:
          'border-[var(--ds-border-soft)] bg-[var(--ds-surface-translucent-selected)] text-foreground',
        brand: 'border-[var(--ds-brand-foreground)] bg-brand-soft text-[var(--ds-on-brand-soft)]',
        success: 'border-[var(--ds-success-border)] bg-success-soft text-success',
        warning: 'border-[var(--ds-warning-border)] bg-warning-soft text-warning',
        danger: 'border-[var(--ds-danger-border)] bg-danger-soft text-danger',
        info: 'border-[var(--ds-info-border)] bg-info-soft text-info',
        special: 'border-[var(--ds-special-border)] bg-special-soft text-special',
      },
    },
    defaultVariants: {
      variant: 'neutral',
    },
  },
);

function Badge({
  className,
  variant = 'neutral',
  render,
  ...props
}: useRender.ComponentProps<'span'> & VariantProps<typeof badgeVariants>) {
  return useRender({
    defaultTagName: 'span',
    props: mergeProps<'span'>({ className: cn(badgeVariants({ variant }), className) }, props),
    render,
    state: { slot: 'badge', variant },
  });
}

export { Badge, badgeVariants };
