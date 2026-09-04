import { describe, expect, it } from 'vitest';

import { adminBreadcrumbsFor } from './admin-navigation-model';

describe('adminBreadcrumbsFor', () => {
  it('derives the trail from the navigation model instead of a second path table', () => {
    // 两套并存必然漂移：改了侧栏分组名而面包屑还显示旧的，没有任何检查会发现。
    expect(adminBreadcrumbsFor('/admin/users')).toEqual([
      { label: '账号管理' },
      { label: '用户账号' },
    ]);
  });

  it('returns a single crumb for a top-level entry', () => {
    expect(adminBreadcrumbsFor('/admin/problems')).toEqual([{ label: '题目管理' }]);
  });

  it('keeps nested routes on their owning entry', () => {
    expect(adminBreadcrumbsFor('/admin/problems/abc/versions/1')).toEqual([{ label: '题目管理' }]);
  });

  it('resolves the admin root to its own dashboard entry', () => {
    expect(adminBreadcrumbsFor('/admin')).toEqual([{ label: 'Dashboard' }]);
  });

  it('never marks the last crumb as a link target', () => {
    // 末项是当前位置，不是可以点的链接。
    for (const path of ['/admin/users', '/admin/problems']) {
      const trail = adminBreadcrumbsFor(path);
      expect(trail.at(-1)).not.toHaveProperty('to');
    }
  });
});
