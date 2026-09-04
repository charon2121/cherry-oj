import { X } from 'lucide-react';
import { type AriaAttributes, type KeyboardEvent, useState } from 'react';

import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/typography';
import { cn } from '@/lib/utils';

type ProblemTagInputProps = Readonly<{
  value: string[];
  onChange: (value: string[]) => void;
  id?: string;
  disabled?: boolean;
  required?: boolean;
  'aria-describedby'?: string;
  'aria-invalid'?: AriaAttributes['aria-invalid'];
}>;

export function ProblemTagInput({
  value,
  onChange,
  id,
  disabled = false,
  'aria-describedby': ariaDescribedBy,
  'aria-invalid': ariaInvalid,
}: ProblemTagInputProps) {
  const [draft, setDraft] = useState('');
  const [message, setMessage] = useState('');

  const addDraft = () => {
    const tag = draft.trim();
    if (!tag) return;
    if (tag.length > 32) {
      setMessage('每个标签不能超过 32 个字符。');
      return;
    }
    if (value.includes(tag)) {
      setMessage('这个标签已经添加。');
      return;
    }
    if (value.length >= 20) {
      setMessage('最多添加 20 个标签。');
      return;
    }
    onChange([...value, tag]);
    setDraft('');
    setMessage('');
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      addDraft();
    } else if (event.key === 'Backspace' && !draft && value.length > 0) {
      onChange(value.slice(0, -1));
    }
  };

  return (
    <div
      className={cn(
        'focus-within:outline-ring border-border bg-surface-translucent py-1x focus-within:border-brand-surface flex min-h-10 flex-wrap items-center gap-2 rounded-sm border px-2 focus-within:outline-1 focus-within:outline-offset-0',
        ariaInvalid && 'border-danger-border',
      )}
    >
      {value.map((tag) => (
        <span
          key={tag}
          className="rounded-micro border-border-soft bg-surface-translucent-selected inline-flex min-h-7 items-center gap-1 border px-2 text-sm"
        >
          {tag}
          <button
            type="button"
            className="focus-visible:outline-ring duration-fast hover:bg-surface-hover inline-flex size-6 items-center justify-center rounded-xs transition-colors focus-visible:outline-1 motion-reduce:transition-none"
            aria-label={`删除标签 ${tag}`}
            disabled={disabled}
            onClick={() => onChange(value.filter((item) => item !== tag))}
          >
            <X className="size-3" aria-hidden="true" />
          </button>
        </span>
      ))}
      <Input
        id={id}
        className="min-w-36 flex-1 border-0 bg-transparent px-1 focus-visible:outline-0"
        value={draft}
        disabled={disabled}
        aria-describedby={ariaDescribedBy}
        aria-invalid={ariaInvalid}
        placeholder={value.length === 0 ? '输入后按 Enter 添加' : '继续添加标签'}
        onBlur={addDraft}
        onChange={(event) => {
          setDraft(event.target.value);
          setMessage('');
        }}
        onKeyDown={onKeyDown}
      />
      {message ? (
        <Text className="text-danger w-full" size="sm" role="status">
          {message}
        </Text>
      ) : null}
    </div>
  );
}
