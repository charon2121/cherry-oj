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
        'sticky top-0 z-20 flex h-[var(--ds-header-height)] items-center border-b border-[var(--ds-border-soft)] bg-[var(--ds-panel)] px-[var(--ds-container-gutter-phone)] sm:px-[var(--ds-container-gutter-tablet)] lg:px-[var(--ds-container-gutter-desktop)]',
        className,
      )}
      {...props}
    >
      <div className="mx-auto flex w-full max-w-[var(--ds-container-max)] min-w-0 items-center gap-[var(--ds-space-6)]">
        <div className="font-display text-foreground shrink-0 text-[length:var(--ds-text-base)] font-[var(--ds-weight-heading)] tracking-[var(--ds-tracking-heading)]">
          {brand}
        </div>
        <nav
          aria-label="主导航"
          className="flex min-w-0 flex-1 items-center gap-[var(--ds-space-5)] overflow-x-auto"
        >
          {links.map((link) => (
            <a
              key={link.href}
              href={link.href}
              aria-current={link.active ? 'page' : undefined}
              className="font-display hover:text-foreground focus-visible:outline-ring aria-[current=page]:text-foreground shrink-0 rounded-[var(--ds-radius-micro)] text-[length:var(--ds-text-cap)] font-[var(--ds-weight-body)] text-[var(--ds-fg-2)] no-underline transition-colors duration-[var(--ds-motion-fast)] focus-visible:outline-2 focus-visible:outline-offset-2 motion-reduce:transition-none"
            >
              {link.label}
            </a>
          ))}
          {children}
        </nav>
        {actions ? (
          <div className="flex shrink-0 items-center gap-[var(--ds-space-3)]">{actions}</div>
        ) : null}
      </div>
    </header>
  );
}

export { NavBar, type NavBarLink, type NavBarProps };
