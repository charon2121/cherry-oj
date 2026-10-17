import { Link } from '@tanstack/react-router';

import { cn } from '@/lib/utils';

type AppBrandProps = Readonly<{
  className?: string;
  destination?: 'admin' | 'site';
}>;

function BrandName() {
  return (
    <span className="inline-flex items-center gap-2 whitespace-nowrap">
      {/* 标志：通过的勾上挂一颗樱桃。与 favicon 是同一个文件（public/brand-mark.svg）。 */}
      <img src="/brand-mark.svg" alt="" className="size-5 shrink-0" />
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
        <BrandName />
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
