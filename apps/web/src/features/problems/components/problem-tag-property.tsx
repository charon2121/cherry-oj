import { Plus, Tag, X } from 'lucide-react';
import { type KeyboardEvent, useRef, useState } from 'react';

import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Text } from '@/components/ui/typography';

import { PropertyButton } from './property-button';

type ProblemTagPropertyProps = Readonly<{
  value: string[];
  onChange: (value: string[]) => void;
  disabled?: boolean;
}>;

// 题目属性卡片中的标签选择器：已有标签以带框小徽章展示并支持一键移除；点击"添加标签"在弹出框内连续回车录入。
export function ProblemTagProperty({ value, onChange, disabled = false }: ProblemTagPropertyProps) {
  const inputRef = useRef<HTMLInputElement>(null);
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
    if (event.nativeEvent.isComposing) return;
    if (event.key === 'Enter' || event.key === ',' || event.key === '，') {
      event.preventDefault();
      addDraft();
    } else if (event.key === 'Backspace' && !draft && value.length > 0) {
      onChange(value.slice(0, -1));
    }
  };

  return (
    <div className="flex min-w-0 flex-wrap items-center gap-1.5">
      {value.map((tag) => (
        <span
          key={tag}
          className="border-border bg-surface-subtle text-fg-2 inline-flex min-h-6 max-w-full min-w-0 items-center gap-1 rounded-md border px-2 py-0.5 text-xs"
        >
          <span className="min-w-0 wrap-anywhere">{tag}</span>
          {disabled ? null : (
            <button
              type="button"
              aria-label={`删除标签 ${tag}`}
              className="text-fg-meta hover:text-foreground focus-visible:outline-ring inline-flex size-4 shrink-0 items-center justify-center rounded-sm focus-visible:outline-1"
              onClick={() => onChange(value.filter((item) => item !== tag))}
            >
              <X className="size-3" aria-hidden="true" />
            </button>
          )}
        </span>
      ))}
      {disabled ? (
        value.length === 0 ? (
          <Text as="span" size="sm" tone="meta" className="px-1.5">
            没有标签
          </Text>
        ) : null
      ) : (
        <Popover
          onOpenChange={(open) => {
            if (!open) {
              addDraft();
              setMessage('');
            }
          }}
        >
          <PopoverTrigger
            render={
              <PropertyButton
                icon={value.length === 0 ? <Tag aria-hidden="true" /> : <Plus aria-hidden="true" />}
                muted
              />
            }
          >
            {value.length === 0 ? '添加标签' : <span className="sr-only">添加标签</span>}
          </PopoverTrigger>
          <PopoverContent align="start" className="w-64 gap-0 p-0" initialFocus={inputRef}>
            <input
              ref={inputRef}
              aria-label="标签名"
              value={draft}
              placeholder={value.length >= 20 ? '已达 20 个上限' : '标签名'}
              onChange={(event) => {
                setDraft(event.target.value);
                setMessage('');
              }}
              onKeyDown={onKeyDown}
              className="text-foreground placeholder:text-fg-meta h-9 w-full bg-transparent px-3.5 text-sm outline-none"
            />
            {message ? (
              <Text className="text-danger px-3.5 py-2" size="sm" role="status">
                {message}
              </Text>
            ) : null}
          </PopoverContent>
        </Popover>
      )}
    </div>
  );
}
