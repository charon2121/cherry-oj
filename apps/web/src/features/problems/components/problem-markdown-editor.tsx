import { Expand, Minimize2 } from 'lucide-react';
import { useId, useState } from 'react';

import { Button } from '@/components/ui/button';
import { TextEditor, type TextEditorSize } from '@/components/ui/text-editor';
import { Text } from '@/components/ui/typography';
import { cn } from '@/lib/utils';

import { SafeMarkdown } from './safe-markdown';

type MarkdownView = 'edit' | 'split' | 'preview';

export function ProblemMarkdownEditor({
  label,
  value,
  onChange,
  onBlur,
  required = false,
  disabled = false,
  description,
  size = 'compact',
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  required?: boolean;
  disabled?: boolean;
  description?: string;
  size?: TextEditorSize;
  placeholder?: string;
}) {
  const [view, setView] = useState<MarkdownView>('edit');
  const [expanded, setExpanded] = useState(false);
  const generatedId = useId();
  const editorId = `markdown-editor-${generatedId}`;
  const descriptionId = `${editorId}-description`;
  const showEditor = view !== 'preview';
  const showPreview = view !== 'edit';

  return (
    <div className="grid gap-[var(--ds-space-2)]">
      <div className="flex flex-wrap items-center justify-between gap-[var(--ds-space-2)]">
        <label
          className="text-[length:var(--ds-text-sm)] font-[var(--ds-weight-body)]"
          htmlFor={editorId}
        >
          {label}
          {required ? <span aria-hidden="true"> *</span> : null}
        </label>
        <div className="flex flex-wrap gap-[var(--ds-space-1)]" aria-label={`${label}视图`}>
          {(['edit', 'split', 'preview'] as const).map((mode) => (
            <Button
              key={mode}
              className={cn(mode === 'split' && 'hidden lg:inline-flex')}
              size="sm"
              variant="toolbar"
              aria-pressed={view === mode}
              onClick={() => setView(mode)}
            >
              {mode === 'edit' ? '编辑' : mode === 'split' ? '分屏' : '预览'}
            </Button>
          ))}
          <Button
            size="sm"
            variant="toolbar"
            aria-pressed={expanded}
            onClick={() => setExpanded((current) => !current)}
          >
            {expanded ? <Minimize2 aria-hidden="true" /> : <Expand aria-hidden="true" />}
            {expanded ? '收起' : '展开'}
          </Button>
        </div>
      </div>
      <div className={cn('grid gap-[var(--ds-space-3)]', view === 'split' && 'lg:grid-cols-2')}>
        {showEditor ? (
          <TextEditor
            id={editorId}
            value={value}
            onChange={onChange}
            readOnly={disabled}
            language="markdown"
            size={expanded ? 'default' : size}
            required={required}
            aria-label={`${label} Markdown 编辑器`}
            {...(onBlur ? { onBlur } : {})}
            {...(placeholder ? { placeholder } : {})}
            {...(description ? { 'aria-describedby': descriptionId } : {})}
          />
        ) : null}
        {showPreview ? (
          <section
            aria-label={`${label}安全预览`}
            className={cn(
              'min-h-32 rounded-[var(--ds-radius-sm)] border border-[var(--ds-border)] bg-[var(--ds-surface-translucent)] p-[var(--ds-space-4)]',
              view === 'split' && 'hidden lg:block',
            )}
          >
            {value.trim() ? (
              <SafeMarkdown value={value} />
            ) : (
              <Text size="sm" tone="muted">
                输入内容后在这里查看安全预览。
              </Text>
            )}
          </section>
        ) : null}
      </div>
      {description ? (
        <Text id={descriptionId} size="sm" tone="muted">
          {description}
        </Text>
      ) : null}
    </div>
  );
}
