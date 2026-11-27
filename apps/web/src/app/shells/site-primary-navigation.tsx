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
        variant === 'desktop' ? 'hidden items-center gap-1.5 sm:flex' : 'grid gap-1 p-3',
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
                ? 'bg-brand-soft text-brand font-heading'
                : 'bg-surface-subtle text-foreground font-heading',
          }}
          className={cn(
            'font-display focus-visible:outline-ring hover:bg-surface-hover hover:text-foreground font-body text-fg-2 duration-fast rounded-lg px-3 py-1.5 text-sm no-underline transition-colors focus-visible:outline-1 focus-visible:outline-offset-0 motion-reduce:transition-none',
            variant === 'mobile' && 'flex min-h-9 w-full items-center',
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
