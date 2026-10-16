/**
 * 表单控件的共享外观，取自 linear.app 实测（docs/design-system/measurements.md）：
 * 高 32px、圆角 8px、0.5px 边、左右 12px、13px 字。
 *
 * 焦点是一条贴边的 1px outline（offset -1px），换成 focus 色。它不占布局，所以不会推挤相邻元素；
 * 0.5px 的边框本身太细，单靠它变色看不出焦点。边框色用 border-strong，保证控件边界 3:1。
 */
export const controlClasses =
  'min-h-8 w-full min-w-0 rounded-lg border-hairline border-border-strong bg-surface px-3 py-1x text-sm leading-body text-foreground transition-[background-color,border-color] duration-fast ease-standard placeholder:text-muted-foreground focus-visible:outline-1 focus-visible:outline-offset-[-1px] focus-visible:outline-ring disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-surface-translucent disabled:text-fg-disabled aria-invalid:border-danger-border motion-reduce:transition-none';
