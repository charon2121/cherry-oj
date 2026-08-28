import { Link, Outlet } from '@tanstack/react-router';
import { Menu } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Container } from '@/components/ui/layout';
import { linkVariants } from '@/components/ui/link';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { Sidebar } from '@/components/ui/sidebar';

import { AdminNavigation } from './admin-navigation';
import { SessionActions } from './session-actions';

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
      <header className="border-border bg-sidebar border-b">
        <Container className="max-w-none">
          <div className="flex min-h-14 flex-wrap items-center gap-2 py-1">
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
            <Link
              to="/admin"
              className={linkVariants({
                size: 'standalone',
                variant: 'muted',
                className: 'font-display shrink-0 tracking-tight',
              })}
            >
              <span className="text-brand">Cherry</span> OJ
              <span className="text-muted-foreground ml-2 text-sm">管理中心</span>
            </Link>
            <Link
              to="/"
              className={linkVariants({
                size: 'standalone',
                variant: 'muted',
                className: 'ml-auto text-sm',
              })}
            >
              返回用户端
            </Link>
            <SessionActions className="ml-0 flex-none" />
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
