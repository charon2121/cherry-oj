import { Link } from '@tanstack/react-router';

import { buttonVariants } from '@/components/ui/button';
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
      className={cn(variant === 'desktop' ? 'hidden items-center gap-1 sm:flex' : 'grid gap-1 p-3')}
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
            className: 'bg-accent text-foreground font-[var(--ds-weight-heading)]',
          }}
          className={buttonVariants({
            size: variant === 'mobile' ? 'md' : 'sm',
            variant: 'ghost',
            className: cn(
              'rounded-md px-3 no-underline',
              variant === 'mobile' && 'w-full justify-start',
            ),
          })}
          onClick={onNavigate}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}

export { SitePrimaryNavigation, type SitePrimaryNavigationProps };
