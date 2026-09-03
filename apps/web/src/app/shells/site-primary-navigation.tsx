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
      className={cn(variant === 'desktop' ? 'hidden items-center gap-5 sm:flex' : 'grid gap-1 p-3')}
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
                ? 'bg-surface-translucent-selected text-foreground'
                : 'text-foreground',
          }}
          className={cn(
            'font-display focus-visible:outline-ring hover:text-foreground text-cap font-body text-fg-2 duration-fast rounded-xs no-underline transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 motion-reduce:transition-none',
            variant === 'mobile' &&
              'hover:bg-surface-translucent-hover flex min-h-8 w-full items-center px-2 py-1',
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
