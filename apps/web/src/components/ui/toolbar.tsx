import { type ReactNode } from 'react';

import { cn } from '@/lib/utils';

import { Pill } from './pill';

// Toolbar 是第 3 层业务组件，构图依据是冻结来源 ui_kits/app/ProblemList.jsx 的两行工具条。
//
// 它替代的是"Panel 包住的 grid 表单 + 一个筛选提交按钮"。来源的过滤是**即时生效**的一排 pill：
// 少一次点击，也少一个需要解释的中间状态。只有当查询确实昂贵（例如全表扫描、跨服务聚合）
// 才回到显式提交，并且要在对应 DESIGN 里说明什么算昂贵。
//
// 第一行：标题 + 计数 + 搜索 + 主操作。计数是来源的习惯——列表页第一眼要能回答"有多少"。
// 第二行：pill 过滤 + toolbar 按钮。两行都用 border-soft 收边，高度对齐 header。
type ToolbarProps = Readonly<{
  actions?: ReactNode;
  children?: ReactNode;
  className?: string;
  /** 计数用准确数字（"1,284 道题"），不要写"很多"。 */
  count?: ReactNode;
  filters?: ReactNode;
  search?: ReactNode;
  title: ReactNode;
  /** 标题层级由页面决定；工具条只负责它的视觉位置。 */
  titleId?: string;
}>;

function Toolbar({
  actions,
  children,
  className,
  count,
  filters,
  search,
  title,
  titleId,
}: ToolbarProps) {
  const hasSecondRow = filters !== undefined || children !== undefined;

  return (
    <div data-slot="toolbar" className={cn('min-w-0', className)}>
      <div className="border-border-soft min-h-header flex min-w-0 flex-wrap items-center gap-3 border-b px-4">
        <span id={titleId} className="font-display font-body text-foreground text-sm">
          {title}
        </span>
        {count === undefined ? null : <span className="text-fg-meta text-cap">{count}</span>}
        <div className="flex-1" />
        {search}
        {actions}
      </div>
      {hasSecondRow ? (
        <div className="border-border-soft py-2x flex min-w-0 flex-wrap items-center gap-2 border-b px-4">
          {filters}
          {children === undefined ? null : (
            <>
              <div className="flex-1" />
              {children}
            </>
          )}
        </div>
      ) : null}
    </div>
  );
}

type ToolbarFilterOption<Value extends string> = Readonly<{ label: ReactNode; value: Value }>;

type ToolbarFilterGroupProps<Value extends string> = Readonly<{
  className?: string;
  label: string;
  onValueChange: (value: Value) => void;
  options: readonly ToolbarFilterOption<Value>[];
  value: Value;
}>;

// 一组互斥的 pill 过滤器。即时生效，没有提交按钮；选中态由 aria-pressed 表达，
// 不只靠背景色，键盘可达。
function ToolbarFilterGroup<Value extends string>({
  className,
  label,
  onValueChange,
  options,
  value,
}: ToolbarFilterGroupProps<Value>) {
  return (
    <div
      data-slot="toolbar-filter-group"
      role="group"
      aria-label={label}
      className={cn('flex min-w-0 flex-wrap items-center gap-2', className)}
    >
      {options.map((option) => (
        <Pill
          key={option.value}
          selected={option.value === value}
          onClick={() => onValueChange(option.value)}
        >
          {option.label}
        </Pill>
      ))}
    </div>
  );
}

export {
  Toolbar,
  ToolbarFilterGroup,
  type ToolbarFilterGroupProps,
  type ToolbarFilterOption,
  type ToolbarProps,
};
