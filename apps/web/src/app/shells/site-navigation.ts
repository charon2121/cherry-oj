type SiteNavigationItem = Readonly<{
  id: 'home';
  label: '首页';
  to: '/';
}>;

const siteNavigationItems: readonly SiteNavigationItem[] = [{ id: 'home', label: '首页', to: '/' }];

export { type SiteNavigationItem, siteNavigationItems };
