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
import { AdminBreadcrumbs } from './admin-breadcrumbs';
import { AdminNavigation } from './admin-navigation';
import { AppBrand } from './app-brand';
import { ThemeSwitcher } from './theme-switcher';

function AdminAppShell() {
  const [navigationOpen, setNavigationOpen] = useState(false);

  return (
    <div className="bg-background text-foreground grid min-h-svh grid-rows-[auto_minmax(0,1fr)] md:h-svh md:grid-cols-[var(--layout-sidebar)_minmax(0,1fr)] md:grid-rows-1 md:overflow-hidden">
      <a
        href="#admin-main"
        className="bg-surface-raised text-foreground focus-visible:outline-ring border-border-strong fixed top-2 left-2 z-60 h-px w-px overflow-hidden rounded-sm border whitespace-nowrap [clip:rect(0,0,0,0)] focus-visible:h-auto focus-visible:w-auto focus-visible:overflow-visible focus-visible:px-3 focus-visible:py-2 focus-visible:outline-1 focus-visible:outline-offset-0 focus-visible:[clip:auto]"
      >
        跳到主要内容
      </a>

      <Sidebar aria-label="管理导航" className="hidden md:flex">
        {/* 左上角是工作区菜单（linear.app 的位置）：产品名、账号、改密、返回用户端与退出都从这里进入；
            主题切换贴在它右侧。主内容面板的顶栏只留面包屑。 */}
        <div className="h-header flex shrink-0 items-center gap-1 px-3">
          <AccountMenu className="min-w-0 flex-1" showSiteEntry trigger="workspace" />
          <ThemeSwitcher />
        </div>
        <nav aria-label="管理侧栏导航" className="contents">
          <AdminNavigation />
        </nav>
      </Sidebar>

      {/* 主内容是一块内嵌面板（linear.app 实测）：侧栏与画布同底，面板四周留 shell-inset、
          比画布亮一档、0.5px 边与 12px 圆角；顶栏与滚动区都在面板内部。窄屏不内嵌。 */}
      <div className="bg-panel md:border-border md:shadow-subtle md:my-shell-inset md:mr-shell-inset md:border-hairline grid min-h-0 min-w-0 grid-rows-[var(--layout-header)_minmax(0,1fr)] md:overflow-hidden md:rounded-xl">
        <header className="border-border-soft bg-panel border-b-hairline sticky top-0 z-40">
          <Container className="h-full max-w-none">
            <div className="flex h-full min-w-0 flex-nowrap items-center gap-2">
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
              <AdminBreadcrumbs className="max-md:hidden" />
              <div className="ml-auto flex items-center gap-1 md:hidden">
                <ThemeSwitcher />
                <AccountMenu className="ml-0" showSiteEntry />
              </div>
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
            <SheetDescription>在总览、题目与账号之间切换。</SheetDescription>
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
