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

// 轻量可编辑代码块：带柔和浅底、1px 边框与 8px 圆角，适合样例输入输出、语言前缀与标程编辑，
// 高度随内容自然增长。
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
        'bg-surface-subtle border-border focus-within:outline-ring flex min-w-0 flex-col rounded-lg border focus-within:outline-1 focus-within:-outline-offset-1',
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
