import { createFileRoute } from '@tanstack/react-router';

import { AdminLanguageListPage } from '@/features/admin-languages/components/admin-language-list-page';

export const Route = createFileRoute('/admin/languages/')({
  component: AdminLanguageListPage,
});
