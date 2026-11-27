import { Outlet, useRouterState } from '@tanstack/react-router';
import { Menu } from 'lucide-react';
import { type ReactNode, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { cn } from '@/lib/utils';

import { AccountMenu } from './account-menu';
import { AppBrand } from './app-brand';
import { SitePrimaryNavigation } from './site-primary-navigation';
import { ThemeSwitcher } from './theme-switcher';

type SiteAppShellProps = Readonly<{ children?: ReactNode }>;

function SiteAppShell({ children }: SiteAppShellProps) {
  const [navigationOpen, setNavigationOpen] = useState(false);
  const coding = useRouterState({
    select: (state) => /^\/problems\/[^/]+\/?$/.test(state.location.pathname),
  });

  return (
    <div
      className={cn(
        'bg-background text-foreground grid grid-cols-[minmax(0,1fr)] grid-rows-[auto_minmax(0,1fr)]',
        coding ? 'h-dvh overflow-hidden' : 'min-h-svh',
      )}
    >
      <a
        href="#site-main"
        className="bg-surface-raised text-foreground focus-visible:outline-ring border-border-strong fixed top-2 left-2 z-60 h-px w-px overflow-hidden rounded-sm border whitespace-nowrap [clip:rect(0,0,0,0)] focus-visible:h-auto focus-visible:w-auto focus-visible:overflow-visible focus-visible:px-3 focus-visible:py-2 focus-visible:outline-1 focus-visible:outline-offset-0 focus-visible:[clip:auto]"
      >
        跳到主要内容
      </a>
      <header className="h-header border-border bg-panel sticky top-0 z-40 border-b">
        <div className="px-gutter-phone sm:px-gutter-tablet lg:px-gutter-desktop h-full">
          <div className="flex h-full min-w-0 flex-nowrap items-center gap-2 sm:gap-6">
            <AppBrand />
            <SitePrimaryNavigation />
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="sm:hidden"
              aria-label="打开主导航"
              aria-expanded={navigationOpen}
              aria-controls="site-mobile-navigation"
              onClick={() => setNavigationOpen(true)}
            >
              <Menu aria-hidden="true" />
              <span className="sr-only">导航</span>
            </Button>
            <ThemeSwitcher className="ml-auto" />
            <AccountMenu className="ml-0" showAdminEntry />
          </div>
        </div>
      </header>

      <main id="site-main" tabIndex={-1} className="min-h-0 min-w-0 outline-none">
        {children ?? <Outlet />}
      </main>

      <Sheet open={navigationOpen} onOpenChange={setNavigationOpen}>
        <SheetContent aria-labelledby="site-navigation-title" closeLabel="关闭主导航">
          <SheetHeader>
            <SheetTitle id="site-navigation-title">主导航</SheetTitle>
          </SheetHeader>
          <SitePrimaryNavigation
            id="site-mobile-navigation"
            variant="mobile"
            onNavigate={() => setNavigationOpen(false)}
          />
        </SheetContent>
      </Sheet>
    </div>
  );
}

export { SiteAppShell, type SiteAppShellProps };
