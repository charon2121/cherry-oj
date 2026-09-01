/**
 * 表单控件的共享外观。
 *
 * 官方 input / textarea / select 各自内联一串等价的 class，其中用 `ring-3 ring-ring/50`
 * 表达焦点、用 `opacity-50` 与 `bg-input/50` 表达禁用、并带按 color scheme 前缀的主题分支。本仓库的设计系统
 * 对这三点都有明确规定（§4 禁止透明度叠加与 theme 分支、§7 焦点必须是 2px outline + offset、
 * disabled 使用专门 token），因此抽到一处集中维护，避免三份实现各自漂移。
 */
export const controlClasses =
  'min-h-10 w-full min-w-0 rounded-sm border border-border-strong bg-input-background px-3 py-2 text-foreground transition-colors duration-[var(--ds-motion-fast)] outline-none placeholder:text-muted-foreground hover:bg-accent focus-visible:border-ring focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:pointer-events-none disabled:cursor-not-allowed disabled:border-border-strong disabled:bg-secondary disabled:text-[var(--ds-fg-disabled)] aria-invalid:border-[var(--ds-danger-border)]';
