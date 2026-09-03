import { Outlet } from '@tanstack/react-router';
import { Menu } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Container } from '@/components/ui/layout';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { Sidebar } from '@/components/ui/sidebar';

import { AccountMenu } from './account-menu';
import { AdminNavigation } from './admin-navigation';
import { AppBrand } from './app-brand';
import { ThemeSwitcher } from './theme-switcher';

function AdminAppShell() {
  const [navigationOpen, setNavigationOpen] = useState(false);

  return (
    <div className="bg-background text-foreground grid min-h-svh grid-rows-[auto_minmax(0,1fr)] md:h-svh md:grid-cols-[var(--ds-sidebar-width)_minmax(0,1fr)] md:grid-rows-1 md:overflow-hidden">
      <a
        href="#admin-main"
        className="bg-surface-raised text-foreground focus-visible:outline-ring border-border-strong fixed top-2 left-2 z-60 h-px w-px overflow-hidden rounded-[var(--ds-radius-sm)] border whitespace-nowrap [clip:rect(0,0,0,0)] focus-visible:h-auto focus-visible:w-auto focus-visible:overflow-visible focus-visible:px-[var(--ds-space-3)] focus-visible:py-[var(--ds-space-2)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:[clip:auto]"
      >
        跳到主要内容
      </a>

      <Sidebar aria-label="管理导航" className="hidden md:flex">
        <div className="flex h-[var(--ds-header-height)] shrink-0 items-center border-b border-[var(--ds-border-soft)] px-[var(--ds-space-3)]">
          <AppBrand destination="admin" />
        </div>
        <nav aria-label="管理侧栏导航" className="contents">
          <AdminNavigation />
        </nav>
      </Sidebar>

      <div className="grid min-h-0 min-w-0 grid-rows-[var(--ds-header-height)_minmax(0,1fr)] md:overflow-hidden">
        <header className="sticky top-0 z-40 border-b border-[var(--ds-border-soft)] bg-[var(--ds-panel)]">
          <Container className="h-full max-w-none">
            <div className="flex h-full min-w-0 flex-nowrap items-center gap-[var(--ds-space-2)]">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="md:hidden"
                aria-label="打开管理导航"
                aria-expanded={navigationOpen}
                aria-controls="admin-mobile-navigation"
                onClick={() => setNavigationOpen(true)}
              >
                <Menu aria-hidden="true" />
                导航
              </Button>
              <div className="md:hidden">
                <AppBrand destination="admin" />
              </div>
              <span className="font-display text-[length:var(--ds-text-cap)] font-[var(--ds-weight-body)] text-[var(--ds-fg-2)] max-md:hidden">
                管理中心
              </span>
              <ThemeSwitcher className="ml-auto" />
              <AccountMenu className="ml-0" showSiteEntry />
            </div>
          </Container>
        </header>
        <main
          id="admin-main"
          tabIndex={-1}
          className="min-h-0 min-w-0 outline-none md:overflow-y-auto"
        >
          <Outlet />
        </main>
      </div>

      <Sheet open={navigationOpen} onOpenChange={setNavigationOpen}>
        <SheetContent aria-labelledby="admin-navigation-title" closeLabel="关闭管理导航">
          <SheetHeader>
            <SheetTitle id="admin-navigation-title">管理导航</SheetTitle>
            <SheetDescription>切换 Dashboard 与管理功能。</SheetDescription>
          </SheetHeader>
          <nav id="admin-mobile-navigation" aria-label="移动管理导航" className="min-h-0 flex-1">
            <AdminNavigation onNavigate={() => setNavigationOpen(false)} />
          </nav>
        </SheetContent>
      </Sheet>
    </div>
  );
}

export { AdminAppShell };
