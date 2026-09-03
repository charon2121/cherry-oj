import { type ComponentProps, type ReactNode } from 'react';

import { cn } from '@/lib/utils';

type NavBarLink = Readonly<{
  active?: boolean;
  href: string;
  label: ReactNode;
}>;

type NavBarProps = ComponentProps<'header'> &
  Readonly<{
    actions?: ReactNode;
    brand?: ReactNode;
    links?: ReadonlyArray<NavBarLink>;
  }>;

function NavBar({
  actions,
  brand = 'Cherry OJ',
  children,
  className,
  links = [],
  ...props
}: NavBarProps) {
  return (
    <header
      data-slot="nav-bar"
      className={cn(
        'h-header border-border-soft bg-panel px-gutter-phone sm:px-gutter-tablet lg:px-gutter-desktop sticky top-0 z-20 flex items-center border-b',
        className,
      )}
      {...props}
    >
      <div className="max-w-page mx-auto flex w-full min-w-0 items-center gap-6">
        <div className="font-display text-foreground font-heading tracking-heading shrink-0 text-base">
          {brand}
        </div>
        <nav aria-label="主导航" className="flex min-w-0 flex-1 items-center gap-5 overflow-x-auto">
          {links.map((link) => (
            <a
              key={link.href}
              href={link.href}
              aria-current={link.active ? 'page' : undefined}
              className="font-display hover:text-foreground focus-visible:outline-ring aria-[current=page]:text-foreground rounded-micro text-cap font-body text-fg-2 duration-fast shrink-0 no-underline transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 motion-reduce:transition-none"
            >
              {link.label}
            </a>
          ))}
          {children}
        </nav>
        {actions ? <div className="flex shrink-0 items-center gap-3">{actions}</div> : null}
      </div>
    </header>
  );
}

export { NavBar, type NavBarLink, type NavBarProps };
