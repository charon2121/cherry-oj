import { createFileRoute } from '@tanstack/react-router';

import { AdminLanguagesPage } from '@/features/admin-languages/components/admin-languages-page';

export const Route = createFileRoute('/admin/languages')({
  component: AdminLanguagesPage,
});
