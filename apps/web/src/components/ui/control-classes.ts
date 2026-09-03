/**
 * 表单控件的共享外观。
 *
 * 官方 input / textarea / select 各自内联一串等价的 class，其中用 `ring-3 ring-ring/50`
 * 表达焦点、用 `opacity-50` 与 `bg-input/50` 表达禁用、并带 `dark:` 分支。本仓库的设计系统
 * 对这三点都有明确规定（§4 禁止透明度叠加与 theme 分支、§7 焦点必须是 2px outline + offset、
 * disabled 使用专门 token），因此抽到一处集中维护，避免三份实现各自漂移。
 */
export const controlClasses =
  'min-h-10 w-full min-w-0 rounded-[var(--ds-radius-sm)] border border-[var(--ds-border)] bg-[var(--ds-surface-translucent)] px-[var(--ds-space-3)] py-[var(--ds-space-3)] text-[length:var(--ds-text-base)] leading-[var(--ds-leading-body)] text-[var(--ds-fg-2)] transition-[background-color,border-color] duration-[var(--ds-motion-fast)] ease-[var(--ds-ease-standard)] placeholder:text-muted-foreground hover:bg-[var(--ds-surface-translucent-hover)] focus-visible:border-[var(--ds-brand-surface)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:pointer-events-none disabled:cursor-not-allowed disabled:border-[var(--ds-border)] disabled:bg-[var(--ds-surface-translucent)] disabled:text-[var(--ds-fg-disabled)] aria-invalid:border-[var(--ds-danger-border)] motion-reduce:transition-none';
