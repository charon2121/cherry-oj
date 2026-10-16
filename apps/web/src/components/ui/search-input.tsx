import { Search } from 'lucide-react';
import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

type SearchInputProps = ComponentProps<'input'> &
  Readonly<{
    containerClassName?: string;
    shortcut?: string;
  }>;

function SearchInput({
  className,
  containerClassName,
  placeholder = '搜索…',
  shortcut,
  type = 'search',
  ...props
}: SearchInputProps) {
  return (
    <label
      data-slot="search-input"
      className={cn(
        // linear.app 侧栏搜索框实测：高 28px、圆角 8px、0.5px 边、比面板略亮的底与极轻阴影。
        'border-border bg-surface shadow-subtle duration-fast ease-standard focus-within:outline-ring border-hairline flex min-h-7 min-w-0 items-center gap-2 rounded-lg px-2 transition-[background-color] focus-within:outline-1 focus-within:outline-offset-[-1px] motion-reduce:transition-none',
        containerClassName,
      )}
    >
      <Search className="text-fg-meta size-[14px] shrink-0" aria-hidden="true" />
      <input
        {...props}
        type={type}
        placeholder={placeholder}
        className={cn(
          'font-body text-foreground placeholder:text-muted-foreground disabled:text-fg-disabled min-w-0 flex-1 border-0 bg-transparent py-px text-sm outline-none disabled:cursor-not-allowed',
          className,
        )}
      />
      {shortcut ? (
        <kbd className="rounded-micro border-border-soft text-tiny text-fg-meta shrink-0 border px-1 py-px font-mono">
          {shortcut}
        </kbd>
      ) : null}
    </label>
  );
}

export { SearchInput, type SearchInputProps };
