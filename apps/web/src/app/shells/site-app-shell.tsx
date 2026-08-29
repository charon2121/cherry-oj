import { Outlet } from '@tanstack/react-router';
import { Menu } from 'lucide-react';
import { type ReactNode, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Container } from '@/components/ui/layout';
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

type SiteAppShellProps = Readonly<{ children?: ReactNode }>;

function SiteAppShell({ children }: SiteAppShellProps) {
  const [navigationOpen, setNavigationOpen] = useState(false);

  return (
    <div className="bg-background text-foreground grid min-h-svh grid-rows-[auto_1fr_auto]">
      <a
        href="#site-main"
        className="bg-surface-raised text-foreground focus-visible:outline-ring border-border-strong fixed top-2 left-2 z-60 -translate-y-20 rounded-sm border px-3 py-2 text-sm focus-visible:translate-y-0 focus-visible:outline-2 focus-visible:outline-offset-2"
      >
        跳到主要内容
      </a>
      <header className="bg-background sticky top-0 z-40">
        <Container>
          <div className="flex min-h-16 min-w-0 flex-nowrap items-center gap-3 py-2">
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
            <AccountMenu showAdminEntry />
          </div>
        </Container>
      </header>

      <main id="site-main" tabIndex={-1} className="min-h-0 min-w-0 outline-none">
        {children ?? <Outlet />}
      </main>

      <footer>
        <Container>
          <div className="flex min-h-12 flex-wrap items-center justify-between gap-2 py-2 text-sm">
            <span className="font-display font-[var(--ds-weight-body)]">Cherry OJ</span>
            <span className="text-muted-foreground">Focused Workspace</span>
          </div>
        </Container>
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
