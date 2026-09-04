/**
 * 表单控件的共享外观。
 *
 * 官方 input / textarea / select 各自内联一串等价的 class，其中用 `ring-3 ring-ring/50`
 * 表达焦点、用 `opacity-50` 与 `bg-input/50` 表达禁用、并带 `dark:` 分支。本仓库对这三点
 * 都有明确规定，因此抽到一处集中维护，避免三份实现各自漂移。
 *
 * 焦点只让那条 1px 边框换颜色，不叠加 ring 或 outline：状态变化只改颜色，不加东西。
 * 加东西会改变元素的尺寸或占位，而对齐是这套系统唯一的结构手段，经不起 1px 的推挤。
 *
 * forced-colors 是唯一的例外。那个模式下颜色由系统接管，border-color 会被覆盖，
 * "边框变色"表达不出焦点，因此回退到一条 1px outline——它在 forced-colors 下会取系统色。
 */
export const controlClasses =
  'min-h-8 w-full min-w-0 rounded-sm border border-border bg-surface-translucent px-3 py-2 text-sm leading-body text-fg-2 transition-[background-color,border-color] duration-fast ease-standard placeholder:text-muted-foreground hover:bg-surface-translucent-hover focus-visible:border-ring focus-visible:outline-none focus-visible:forced-colors:outline-1 focus-visible:forced-colors:outline-solid focus-visible:forced-colors:outline-offset-0 disabled:pointer-events-none disabled:cursor-not-allowed disabled:border-border disabled:bg-surface-translucent disabled:text-fg-disabled aria-invalid:border-danger-border motion-reduce:transition-none';
