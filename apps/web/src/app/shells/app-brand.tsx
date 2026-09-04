import { Link } from '@tanstack/react-router';

import { cn } from '@/lib/utils';

type AppBrandProps = Readonly<{
  className?: string;
  destination?: 'admin' | 'site';
}>;

function BrandName({ showMark = false }: Readonly<{ showMark?: boolean }>) {
  return (
    <span className="inline-flex items-center gap-2 whitespace-nowrap">
      {showMark ? (
        <span aria-hidden="true" className="bg-primary size-4x shrink-0 rounded-xs" />
      ) : null}
      <span>Cherry OJ</span>
    </span>
  );
}

function AppBrand({ className, destination = 'site' }: AppBrandProps) {
  const classes = cn(
    'text-foreground visited:text-foreground focus-visible:outline-ring inline-flex min-h-8 min-w-0 shrink-0 items-center gap-2 rounded-xs font-display font-heading tracking-heading no-underline transition-colors duration-fast focus-visible:outline-1 focus-visible:outline-offset-0 motion-reduce:transition-none',
    destination === 'admin' ? 'px-1 text-sm' : 'text-base',
    className,
  );

  if (destination === 'admin') {
    return (
      <Link to="/admin" className={classes} aria-label="Cherry OJ 管理中心">
        <BrandName showMark />
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
