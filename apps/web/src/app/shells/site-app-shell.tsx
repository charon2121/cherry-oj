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
import { ThemeSwitcher } from './theme-switcher';

type SiteAppShellProps = Readonly<{ children?: ReactNode }>;

function SiteAppShell({ children }: SiteAppShellProps) {
  const [navigationOpen, setNavigationOpen] = useState(false);

  return (
    <div className="bg-background text-foreground grid min-h-svh grid-rows-[auto_1fr_auto]">
      <a
        href="#site-main"
        className="bg-surface-raised text-foreground focus-visible:outline-ring border-border-strong fixed top-2 left-2 z-60 h-px w-px overflow-hidden rounded-[var(--ds-radius-sm)] border whitespace-nowrap [clip:rect(0,0,0,0)] focus-visible:h-auto focus-visible:w-auto focus-visible:overflow-visible focus-visible:px-[var(--ds-space-3)] focus-visible:py-[var(--ds-space-2)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:[clip:auto]"
      >
        跳到主要内容
      </a>
      <header className="sticky top-0 z-40 h-[var(--ds-header-height)] border-b border-[var(--ds-border-soft)] bg-[var(--ds-panel)]">
        <div className="h-full px-[var(--ds-container-gutter-phone)] sm:px-[var(--ds-container-gutter-tablet)] lg:px-[var(--ds-container-gutter-desktop)]">
          <div className="mx-auto flex h-full max-w-[var(--ds-container-max)] min-w-0 flex-nowrap items-center gap-[var(--ds-space-2)] sm:gap-[var(--ds-space-6)]">
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

      <footer className="border-t border-[var(--ds-border-soft)]">
        <Container>
          <div className="flex min-h-12 flex-wrap items-center justify-between gap-[var(--ds-space-2)] py-[var(--ds-space-2)] text-[length:var(--ds-text-sm)]">
            <span className="font-display font-[var(--ds-weight-body)]">Cherry OJ</span>
            <span className="text-[var(--ds-fg-meta)]">Focused Workspace</span>
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
