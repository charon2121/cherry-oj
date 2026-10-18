import { createFileRoute } from '@tanstack/react-router';

import { AdminLanguageEditPage } from '@/features/admin-languages/components/admin-language-edit-page';

export const Route = createFileRoute('/admin/languages/$languageId')({
  component: AdminLanguageEditRoute,
});

function AdminLanguageEditRoute() {
  const { languageId } = Route.useParams();
  return <AdminLanguageEditPage languageId={languageId} />;
}
