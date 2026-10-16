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

// 属性栏里的标签（linear.app 的 Labels）：已有标签是小胶囊，悬停出现删除；"添加标签"点开一个
// 只有输入框的浮层，Enter 或逗号添加，可以连续添加。
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
    <div className="flex min-w-0 flex-wrap items-center gap-1">
      {value.map((tag) => (
        <span
          key={tag}
          className="group/tag bg-surface shadow-ring text-fg-2 inline-flex h-6 max-w-full items-center gap-1 rounded-full pr-1 pl-2 text-xs"
        >
          <span className="min-w-0 truncate">{tag}</span>
          {disabled ? null : (
            <button
              type="button"
              aria-label={`删除标签 ${tag}`}
              className="text-fg-meta hover:text-foreground focus-visible:outline-ring inline-flex size-4 items-center justify-center rounded-full focus-visible:outline-1"
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
          {/* 浮层打开即可输入（linear.app 的属性浮层第一行就是输入框）。 */}
          <PopoverContent align="start" className="w-64 gap-0 p-0" initialFocus={inputRef}>
            <input
              ref={inputRef}
              aria-label="标签名"
              value={draft}
              placeholder={value.length >= 20 ? '已达 20 个上限' : '输入标签，Enter 添加'}
              onChange={(event) => {
                setDraft(event.target.value);
                setMessage('');
              }}
              onKeyDown={onKeyDown}
              className="text-foreground placeholder:text-fg-meta h-9 w-full bg-transparent px-3.5 text-sm outline-none"
            />
            {message ? (
              <Text
                className="text-danger border-border-soft border-t-hairline px-3.5 py-2"
                size="sm"
                role="status"
              >
                {message}
              </Text>
            ) : null}
          </PopoverContent>
        </Popover>
      )}
    </div>
  );
}
