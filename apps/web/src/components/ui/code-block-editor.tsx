import type { ReactNode } from 'react';

import { TextEditor, type TextEditorLanguage } from '@/components/ui/text-editor';
import { cn } from '@/lib/utils';

type CodeBlockEditorProps = Readonly<{
  /** 块顶部的小标题，例如"输入"；不传就没有标题行。 */
  caption?: ReactNode;
  value: string;
  onChange: (value: string) => void;
  'aria-label': string;
  language?: TextEditorLanguage;
  readOnly?: boolean;
  placeholder?: string;
  className?: string;
}>;

// 可编辑的代码块，样子取自 linear.app 正文里的代码块（docs/design-system/measurements.md「描述编辑器」）：
// 比面板亮一档的底、0.5px 边、6px 圆角、12px/20px 等宽。只是一块等宽文本——没有行号、语言标签和
// 复制按钮；需要区分时（样例的输入/输出）顶部放一个小标题。高度随内容增长。
function CodeBlockEditor({
  caption,
  value,
  onChange,
  'aria-label': ariaLabel,
  language = 'plain',
  readOnly = false,
  placeholder,
  className,
}: CodeBlockEditorProps) {
  return (
    <div
      data-slot="code-block-editor"
      className={cn(
        'bg-surface border-border border-hairline focus-within:outline-ring flex min-w-0 flex-col rounded-md focus-within:outline-1 focus-within:-outline-offset-1',
        className,
      )}
    >
      {caption === undefined ? (
        <div className="h-3 shrink-0" />
      ) : (
        <div className="text-fg-meta font-body flex h-8 shrink-0 items-center truncate px-4 text-xs">
          {caption}
        </div>
      )}
      <TextEditor
        appearance="bare"
        size="auto"
        language={language}
        value={value}
        readOnly={readOnly}
        {...(placeholder === undefined ? {} : { placeholder })}
        aria-label={ariaLabel}
        onChange={onChange}
        className="pb-1.5"
      />
    </div>
  );
}

export { CodeBlockEditor, type CodeBlockEditorProps };
