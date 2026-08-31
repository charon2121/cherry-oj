type SiteNavigationItem = Readonly<{
  id: 'home' | 'problems';
  label: '首页' | '题库';
  to: '/' | '/problems';
}>;

const siteNavigationItems: readonly SiteNavigationItem[] = [
  { id: 'home', label: '首页', to: '/' },
  { id: 'problems', label: '题库', to: '/problems' },
];

export { type SiteNavigationItem, siteNavigationItems };
