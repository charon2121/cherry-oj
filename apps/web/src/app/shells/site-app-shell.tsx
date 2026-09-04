import { Outlet } from '@tanstack/react-router';
import { Menu } from 'lucide-react';
import { type ReactNode, useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';

import { AccountMenu } from './account-menu';
import { AppBrand } from './app-brand';
import { SitePrimaryNavigation } from './site-primary-navigation';
import { ThemeSwitcher } from './theme-switcher';

type SiteAppShellProps = Readonly<{ children?: ReactNode }>;

function SiteAppShell({ children }: SiteAppShellProps) {
  const [navigationOpen, setNavigationOpen] = useState(false);

  return (
    <div className="bg-background text-foreground grid min-h-svh grid-rows-[auto_1fr_auto]">
      <a
        href="#site-main"
        className="bg-surface-raised text-foreground focus-visible:outline-ring border-border-strong fixed top-2 left-2 z-60 h-px w-px overflow-hidden rounded-sm border whitespace-nowrap [clip:rect(0,0,0,0)] focus-visible:h-auto focus-visible:w-auto focus-visible:overflow-visible focus-visible:px-3 focus-visible:py-2 focus-visible:outline-1 focus-visible:outline-offset-0 focus-visible:[clip:auto]"
      >
        跳到主要内容
      </a>
      <header className="h-header border-border-soft bg-panel sticky top-0 z-40 border-b">
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
              导航
            </Button>
            <ThemeSwitcher className="ml-auto" />
            <AccountMenu className="ml-0" showAdminEntry />
          </div>
        </div>
      </header>

      <main id="site-main" tabIndex={-1} className="min-h-0 min-w-0 outline-none">
        {children ?? <Outlet />}
      </main>

      <footer className="border-border-soft border-t">
        <div className="px-gutter-phone sm:px-gutter-tablet lg:px-gutter-desktop">
          <div className="flex min-h-12 flex-wrap items-center justify-between gap-2 py-2 text-sm">
            <span className="font-display font-body">Cherry OJ</span>
            <span className="text-fg-meta">Focused Workspace</span>
          </div>
        </div>
      </footer>

      <Sheet open={navigationOpen} onOpenChange={setNavigationOpen}>
        <SheetContent aria-labelledby="site-navigation-title" closeLabel="关闭主导航">
          <SheetHeader>
            <SheetTitle id="site-navigation-title">主导航</SheetTitle>
            <SheetDescription>前往 Cherry OJ 当前可用页面。</SheetDescription>
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
