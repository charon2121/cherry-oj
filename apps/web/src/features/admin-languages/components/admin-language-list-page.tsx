import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { Braces } from 'lucide-react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import { DataList, dataListRowLinkClasses } from '@/components/ui/data-list';
import { ListPageTemplate } from '@/components/ui/page-templates';
import { Toolbar } from '@/components/ui/toolbar';
import { apiErrorMessage } from '@/lib/api/api-error-message';

import { adminLanguageKeys, listLanguageSettings } from '../api/admin-languages-api';
import { useRedirectWhenSignedOut } from './use-redirect-when-signed-out';

const updatedAtFormat = new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' });

// 构图照题目管理列表（ListPageTemplate + Toolbar + DataList）。语言名只有几个字、条目很少，
// 按 PROMPT「列表页（行只有几个短字段）」用 column + packed。点一行进入这门语言的前缀编辑页
// （linear.app Settings › Templates：列表一行一项，点开是单独的编辑页）。
export function AdminLanguageListPage() {
  const languages = useQuery({
    queryKey: adminLanguageKeys.all,
    queryFn: ({ signal }) => listLanguageSettings(signal),
  });
  useRedirectWhenSignedOut(languages.error, '/admin/languages');

  const state = languages.isPending ? (
    <AsyncState variant="loading" size="inline" title="正在加载语言…" progressLabel="正在加载语言…">
      {null}
    </AsyncState>
  ) : languages.isError ? (
    <AsyncState
      variant="error"
      size="inline"
      title="语言加载失败"
      action={
        <Button variant="secondary" onClick={() => void languages.refetch()}>
          重试
        </Button>
      }
    >
      {apiErrorMessage(languages.error, '请稍后重试。')}
    </AsyncState>
  ) : undefined;

  return (
    <ListPageTemplate
      title="语言"
      width="column"
      toolbar={<Toolbar title="语言" className="md:hidden" />}
      state={state}
    >
      <DataList
        caption="语言"
        align="packed"
        rows={languages.data ?? []}
        rowKey={(language) => language.id}
        rowInteractive
        renderRow={(language) => ({
          leading: <Braces aria-hidden="true" className="text-fg-meta size-4" />,
          id: (
            <span className="block w-14 truncate" title={language.id}>
              {language.id}
            </span>
          ),
          title: (
            <Link
              to="/admin/languages/$languageId"
              params={{ languageId: language.id }}
              className={dataListRowLinkClasses}
            >
              {language.displayName}
            </Link>
          ),
          trailing: (
            <time dateTime={language.updatedAt} className="tabular-nums" title="更新日期">
              {updatedAtFormat.format(new Date(language.updatedAt))}
            </time>
          ),
        })}
      />
    </ListPageTemplate>
  );
}
