import { describe, expect, it } from 'vitest';

import {
  adminNavigationEntries,
  isAdminNavigationGroup,
  isAdminNavigationLeafActive,
} from './admin-navigation-model';

describe('admin navigation model', () => {
  it('keeps the agreed overview, problems, languages, accounts order as flat entries', () => {
    expect(adminNavigationEntries.map((entry) => entry.label)).toEqual([
      '总览',
      '题目',
      '语言',
      '账号',
    ]);
    expect(adminNavigationEntries.some(isAdminNavigationGroup)).toBe(false);
  });

  it('treats both dashboard routes as the same active destination', () => {
    const dashboard = adminNavigationEntries.find((entry) => entry.id === 'dashboard');
    if (!dashboard || isAdminNavigationGroup(dashboard)) throw new Error('Dashboard is missing.');

    expect(isAdminNavigationLeafActive(dashboard, '/admin')).toBe(true);
    expect(isAdminNavigationLeafActive(dashboard, '/admin/dashborad')).toBe(true);
    expect(isAdminNavigationLeafActive(dashboard, '/admin/users')).toBe(false);
  });
});
