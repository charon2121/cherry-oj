import { Link, Outlet } from '@tanstack/react-router';
import { ArrowLeft, Menu } from 'lucide-react';
import { useState } from 'react';

import { Button, buttonVariants } from '@/components/ui/button';
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

function AdminAppShell() {
  const [navigationOpen, setNavigationOpen] = useState(false);

  return (
    <div className="bg-background text-foreground grid min-h-svh grid-rows-[auto_1fr]">
      <a
        href="#admin-main"
        className="bg-surface-raised text-foreground focus-visible:outline-ring border-border-strong fixed top-2 left-2 z-60 -translate-y-20 rounded-sm border px-3 py-2 text-sm focus-visible:translate-y-0 focus-visible:outline-2 focus-visible:outline-offset-2"
      >
        跳到主要内容
      </a>
      <header className="bg-background sticky top-0 z-40">
        <Container className="max-w-none">
          <div className="flex min-h-16 min-w-0 flex-nowrap items-center gap-3 py-2">
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
            <AppBrand destination="admin" />
            <Link
              to="/"
              className={buttonVariants({
                size: 'sm',
                variant: 'ghost',
                className: 'ml-auto rounded-md px-3 no-underline',
              })}
            >
              <ArrowLeft aria-hidden="true" className="size-4" />
              <span className="hidden sm:inline">返回</span>用户端
            </Link>
            <AccountMenu className="ml-0" />
          </div>
        </Container>
      </header>

      <div className="grid min-h-0 min-w-0 md:grid-cols-[15rem_minmax(0,1fr)]">
        <Sidebar aria-label="管理导航" className="hidden md:flex">
          <nav aria-label="管理侧栏导航" className="contents">
            <AdminNavigation />
          </nav>
        </Sidebar>
        <main id="admin-main" tabIndex={-1} className="min-h-0 min-w-0 outline-none">
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
