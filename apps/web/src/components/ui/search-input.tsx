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
        'focus-within:outline-ring flex min-h-8 min-w-0 items-center gap-[var(--ds-space-2)] rounded-[var(--ds-radius-sm)] border border-[var(--ds-border)] bg-[var(--ds-surface-translucent)] px-[var(--ds-space-2)] py-[var(--ds-space-1)] transition-[background-color,border-color] duration-[var(--ds-motion-fast)] ease-[var(--ds-ease-standard)] focus-within:border-[var(--ds-brand-surface)] focus-within:outline-2 focus-within:outline-offset-2 motion-reduce:transition-none',
        containerClassName,
      )}
    >
      <Search className="size-[14px] shrink-0 text-[var(--ds-fg-meta)]" aria-hidden="true" />
      <input
        {...props}
        type={type}
        placeholder={placeholder}
        className={cn(
          'font-body text-foreground placeholder:text-muted-foreground min-w-0 flex-1 border-0 bg-transparent py-px text-[length:var(--ds-text-sm)] outline-none disabled:cursor-not-allowed disabled:text-[var(--ds-fg-disabled)]',
          className,
        )}
      />
      {shortcut ? (
        <kbd className="shrink-0 rounded-[var(--ds-radius-micro)] border border-[var(--ds-border-soft)] px-[var(--ds-space-1)] py-px font-mono text-[length:var(--ds-text-tiny)] text-[var(--ds-fg-meta)]">
          {shortcut}
        </kbd>
      ) : null}
    </label>
  );
}

export { SearchInput, type SearchInputProps };
