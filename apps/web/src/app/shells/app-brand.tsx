import { Link } from '@tanstack/react-router';

import { cn } from '@/lib/utils';

type AppBrandProps = Readonly<{
  className?: string;
  destination?: 'admin' | 'site';
}>;

function BrandName() {
  return (
    <span className="whitespace-nowrap">
      <span className="text-brand">Cherry</span> OJ
    </span>
  );
}

function AppBrand({ className, destination = 'site' }: AppBrandProps) {
  const classes = cn(
    'text-foreground visited:text-foreground focus-visible:outline-ring inline-flex min-h-8 min-w-0 shrink-0 items-center gap-2 rounded-md px-1 font-display font-[var(--ds-weight-heading)] tracking-tight no-underline transition-colors duration-[var(--ds-motion-fast)] focus-visible:outline-2 focus-visible:outline-offset-2',
    className,
  );

  if (destination === 'admin') {
    return (
      <Link to="/admin" className={classes} aria-label="Cherry OJ 管理中心">
        <span className="hidden sm:inline">
          <BrandName />
        </span>
        <span className="text-muted-foreground text-sm">管理中心</span>
      </Link>
    );
  }

  return (
    <Link to="/" className={classes} aria-label="Cherry OJ 首页">
      <BrandName />
    </Link>
  );
}

export { AppBrand, type AppBrandProps };
