import { BookOpen, Braces, LayoutDashboard, type LucideIcon, Users } from 'lucide-react';

type AdminNavigationLeaf = Readonly<{
  activePaths: readonly string[];
  icon?: LucideIcon;
  id: string;
  label: string;
  to: '/admin' | '/admin/users' | '/admin/problems' | '/admin/languages';
}>;

type AdminNavigationGroup = Readonly<{
  children: readonly AdminNavigationLeaf[];
  icon: LucideIcon;
  id: string;
  label: string;
}>;

type AdminNavigationEntry = AdminNavigationGroup | AdminNavigationLeaf;

// 几个平级入口，名字用对象而不是"××管理"：侧栏本身就在管理端里，"管理"两个字不带信息。
// 只有一个子项的分组会让人多点一次才能到达，因此不再使用。
const adminNavigationEntries: readonly AdminNavigationEntry[] = [
  {
    activePaths: ['/admin', '/admin/dashborad'],
    icon: LayoutDashboard,
    id: 'dashboard',
    label: '总览',
    to: '/admin',
  },
  {
    activePaths: ['/admin/problems'],
    icon: BookOpen,
    id: 'problems',
    label: '题目',
    to: '/admin/problems',
  },
  {
    // 语言是平台级配置（核心代码的前缀），不属于某一道题，所以单独成页而不是放进题目工作台。
    activePaths: ['/admin/languages'],
    icon: Braces,
    id: 'languages',
    label: '语言',
    to: '/admin/languages',
  },
  {
    activePaths: ['/admin/users'],
    icon: Users,
    id: 'users',
    label: '账号',
    to: '/admin/users',
  },
];

function isAdminNavigationGroup(entry: AdminNavigationEntry): entry is AdminNavigationGroup {
  return 'children' in entry;
}

function isAdminNavigationLeafActive(item: AdminNavigationLeaf, pathname: string) {
  return item.activePaths.some(
    (activePath) =>
      pathname === activePath || (activePath !== '/admin' && pathname.startsWith(`${activePath}/`)),
  );
}

function isAdminNavigationGroupActive(group: AdminNavigationGroup, pathname: string) {
  return group.children.some((item) => isAdminNavigationLeafActive(item, pathname));
}

type AdminBreadcrumb = Readonly<{ label: string; to?: AdminNavigationLeaf['to'] }>;

/**
 * 面包屑由导航模型推出，不另建一套路径表。
 *
 * 两套并存必然漂移：改了侧栏的分组名，面包屑还显示旧的，而没有任何检查会发现。
 * 这里只做一件事——在既有的 entries 里找到与当前 pathname 匹配的那条链。
 *
 * 末项不给 `to`：它是当前位置，不是可以点的链接。
 */
function adminBreadcrumbsFor(
  pathname: string,
  pageCrumb: string | null = null,
): readonly AdminBreadcrumb[] {
  // 页面给出当前对象名（例如题目标题）时追加为末项，它所在的栏目因此变成可点的链接。
  const withPage = (section: AdminBreadcrumb, to: AdminNavigationLeaf['to']) =>
    pageCrumb === null ? [section] : [{ ...section, to }, { label: pageCrumb }];
  for (const entry of adminNavigationEntries) {
    if (isAdminNavigationGroup(entry)) {
      const leaf = entry.children.find((child) => isAdminNavigationLeafActive(child, pathname));
      if (leaf !== undefined)
        return [{ label: entry.label }, ...withPage({ label: leaf.label }, leaf.to)];
      continue;
    }
    if (isAdminNavigationLeafActive(entry, pathname))
      return withPage({ label: entry.label }, entry.to);
  }
  return [];
}

export {
  type AdminBreadcrumb,
  adminBreadcrumbsFor,
  adminNavigationEntries,
  type AdminNavigationEntry,
  type AdminNavigationGroup,
  type AdminNavigationLeaf,
  isAdminNavigationGroup,
  isAdminNavigationGroupActive,
  isAdminNavigationLeafActive,
};
