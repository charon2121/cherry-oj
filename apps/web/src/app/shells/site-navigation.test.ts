import { describe, expect, it } from 'vitest';

import { siteNavigationItems } from './site-navigation';

describe('site navigation model', () => {
  it('publishes only routes that already exist', () => {
    expect(siteNavigationItems).toEqual([{ id: 'home', label: '首页', to: '/' }]);
  });
});
