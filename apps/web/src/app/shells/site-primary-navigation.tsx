import { Link } from '@tanstack/react-router';

import { cn } from '@/lib/utils';

import { siteNavigationItems } from './site-navigation';

type SitePrimaryNavigationProps = Readonly<{
  id?: string;
  onNavigate?: () => void;
  variant?: 'desktop' | 'mobile';
}>;

function SitePrimaryNavigation({
  id,
  onNavigate,
  variant = 'desktop',
}: SitePrimaryNavigationProps) {
  return (
    <nav
      id={id}
      aria-label={variant === 'mobile' ? '移动主导航' : '主导航'}
      className={cn(
        variant === 'desktop'
          ? 'hidden items-center gap-[var(--ds-space-5)] sm:flex'
          : 'grid gap-[var(--ds-space-1)] p-[var(--ds-space-3)]',
      )}
    >
      {siteNavigationItems.map((item) => (
        <Link
          key={item.id}
          to={item.to}
          activeOptions={{ exact: item.to === '/' }}
          {...(item.to === '/problems'
            ? { search: { sort: 'UPDATED_DESC' as const, size: 20 } }
            : {})}
          activeProps={{
            'aria-current': 'page',
            className:
              variant === 'mobile'
                ? 'bg-[var(--ds-surface-translucent-selected)] text-foreground'
                : 'text-foreground',
          }}
          className={cn(
            'font-display focus-visible:outline-ring hover:text-foreground rounded-[var(--ds-radius-xs)] text-[length:var(--ds-text-cap)] font-[var(--ds-weight-body)] text-[var(--ds-fg-2)] no-underline transition-colors duration-[var(--ds-motion-fast)] focus-visible:outline-2 focus-visible:outline-offset-2 motion-reduce:transition-none',
            variant === 'mobile' &&
              'flex min-h-8 w-full items-center px-[var(--ds-space-2)] py-[var(--ds-space-1)] hover:bg-[var(--ds-surface-translucent-hover)]',
          )}
          onClick={onNavigate}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}

export { SitePrimaryNavigation, type SitePrimaryNavigationProps };
