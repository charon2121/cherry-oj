import { type ReactNode } from 'react';

import { cn } from '@/lib/utils';

import { Pill } from './pill';

// 列表页顶部工具条：主标题、计数、检索框与主操作位于第一行，下拉/分段筛选位于第二行；
// 不提供标题时收敛为单行（左侧视图切换，右侧搜索与主操作）。
type ToolbarProps = Readonly<{
  actions?: ReactNode;
  children?: ReactNode;
  className?: string;
  /** 计数用准确数字（"本批 20 道题"），不要写模糊词。 */
  count?: ReactNode;
  filters?: ReactNode;
  search?: ReactNode;
  /** 页面名称已在应用顶栏面包屑中展示时可省略。 */
  title?: ReactNode;
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

  if (title === undefined) {
    return (
      <div
        data-slot="toolbar"
        className={cn('mb-4 flex min-h-9 min-w-0 flex-wrap items-center gap-2.5', className)}
      >
        {filters}
        <div className="flex-1" />
        {search}
        {children}
        {actions}
      </div>
    );
  }

  return (
    <div data-slot="toolbar" className={cn('mb-4 flex min-w-0 flex-col gap-3', className)}>
      <div className="flex min-h-9 min-w-0 flex-wrap items-center gap-3">
        <span
          id={titleId}
          className="font-display font-heading text-foreground tracking-heading text-xl"
        >
          {title}
        </span>
        {count === undefined ? null : (
          <span className="bg-surface-subtle text-fg-meta rounded-full px-2.5 py-0.5 text-xs tabular-nums">
            {count}
          </span>
        )}
        <div className="flex-1" />
        {search}
        {actions}
      </div>
      {hasSecondRow ? (
        <div className="flex min-w-0 flex-wrap items-center gap-2">
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
