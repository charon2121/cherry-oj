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
        'border-border bg-surface-translucent duration-fast ease-standard focus-within:border-ring flex min-h-8 min-w-0 items-center gap-2 rounded-sm border px-2 py-1 transition-[background-color,border-color] motion-reduce:transition-none focus-within:forced-colors:outline-1 focus-within:forced-colors:outline-solid',
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
