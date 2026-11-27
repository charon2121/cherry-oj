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
        'border-border-solid bg-surface shadow-ring hover:border-border-strong duration-fast ease-standard focus-within:outline-ring flex min-h-8 min-w-0 items-center gap-2 rounded-lg border px-2.5 transition-[background-color,border-color] focus-within:outline-1 focus-within:outline-offset-[-1px] motion-reduce:transition-none',
        containerClassName,
      )}
    >
      <Search className="text-fg-meta size-4 shrink-0" aria-hidden="true" />
      <input
        {...props}
        type={type}
        placeholder={placeholder}
        className={cn(
          'font-body text-foreground placeholder:text-muted-foreground disabled:text-fg-disabled min-w-0 flex-1 border-0 bg-transparent py-0.5 text-sm outline-none disabled:cursor-not-allowed',
          className,
        )}
      />
      {shortcut ? (
        <kbd className="rounded-micro border-border bg-surface-subtle text-tiny text-fg-meta shrink-0 border px-1.5 py-px font-mono">
          {shortcut}
        </kbd>
      ) : null}
    </label>
  );
}

export { SearchInput, type SearchInputProps };
