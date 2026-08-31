import { BookOpen, LayoutDashboard, type LucideIcon, Users } from 'lucide-react';

type AdminNavigationLeaf = Readonly<{
  activePaths: readonly string[];
  icon?: LucideIcon;
  id: string;
  label: string;
  to: '/admin' | '/admin/users' | '/admin/problems';
}>;

type AdminNavigationGroup = Readonly<{
  children: readonly AdminNavigationLeaf[];
  icon: LucideIcon;
  id: string;
  label: string;
}>;

type AdminNavigationEntry = AdminNavigationGroup | AdminNavigationLeaf;

const adminNavigationEntries: readonly AdminNavigationEntry[] = [
  {
    activePaths: ['/admin/problems'],
    icon: BookOpen,
    id: 'problems',
    label: '题目管理',
    to: '/admin/problems',
  },
  {
    activePaths: ['/admin', '/admin/dashborad'],
    icon: LayoutDashboard,
    id: 'dashboard',
    label: 'Dashboard',
    to: '/admin',
  },
  {
    children: [
      {
        activePaths: ['/admin/users'],
        id: 'users',
        label: '用户账号',
        to: '/admin/users',
      },
    ],
    icon: Users,
    id: 'account',
    label: '账号管理',
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

export {
  adminNavigationEntries,
  type AdminNavigationEntry,
  type AdminNavigationGroup,
  type AdminNavigationLeaf,
  isAdminNavigationGroup,
  isAdminNavigationGroupActive,
  isAdminNavigationLeafActive,
};
