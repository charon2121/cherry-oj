import { describe, expect, it } from 'vitest';

import {
  adminNavigationEntries,
  isAdminNavigationGroup,
  isAdminNavigationGroupActive,
  isAdminNavigationLeafActive,
} from './admin-navigation-model';

describe('admin navigation model', () => {
  it('treats both dashboard routes as the same active destination', () => {
    const dashboard = adminNavigationEntries.find((entry) => entry.id === 'dashboard');
    if (!dashboard || isAdminNavigationGroup(dashboard)) throw new Error('Dashboard is missing.');

    expect(isAdminNavigationLeafActive(dashboard, '/admin')).toBe(true);
    expect(isAdminNavigationLeafActive(dashboard, '/admin/dashborad')).toBe(true);
    expect(isAdminNavigationLeafActive(dashboard, '/admin/users')).toBe(false);
  });

  it('activates the account group only for its real leaf route', () => {
    const account = adminNavigationEntries.find((entry) => entry.id === 'account');
    if (!account || !isAdminNavigationGroup(account)) throw new Error('Account group is missing.');

    expect(isAdminNavigationGroupActive(account, '/admin/users')).toBe(true);
    expect(isAdminNavigationGroupActive(account, '/admin')).toBe(false);
    expect(account.children.map((item) => item.label)).toEqual(['用户账号']);
  });
});
