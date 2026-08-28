import { createFileRoute } from '@tanstack/react-router';

import { SiteAppShell } from '@/app/shells/site-app-shell';

export const Route = createFileRoute('/_site')({
  component: SiteAppShell,
});
