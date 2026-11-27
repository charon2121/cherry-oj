/**
 * 表单输入控件共享样式：36px 标准高度、8px 圆角、1px 清晰边框与微环投影；
 * 聚焦使用 1px 内缩 outline，不改变盒模型尺寸。
 */
export const controlClasses =
  'min-h-9 w-full min-w-0 rounded-lg border border-border-solid bg-surface shadow-ring px-3 py-1.5 text-sm leading-body text-foreground transition-[background-color,border-color,box-shadow] duration-fast ease-standard placeholder:text-muted-foreground hover:border-border-strong focus-visible:outline-1 focus-visible:outline-offset-[-1px] focus-visible:outline-ring disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-surface-translucent disabled:text-fg-disabled aria-invalid:border-danger-border motion-reduce:transition-none';
