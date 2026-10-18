import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { Braces } from 'lucide-react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import { DataList, dataListRowLinkClasses } from '@/components/ui/data-list';
import { ListPageTemplate } from '@/components/ui/page-templates';
import { Toolbar } from '@/components/ui/toolbar';
import { apiErrorMessage } from '@/features/problems/components/problem-workbench-dialogs';

import { adminLanguageKeys, listLanguageSettings } from '../api/admin-languages-api';
import { prefixLineCount, updatedAtFormat } from '../lib/prefix';
import { useRedirectWhenSignedOut } from './use-redirect-when-signed-out';

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
      toolbar={
        <Toolbar title="语言" count={languages.data ? `${languages.data.length} 门` : undefined} />
      }
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
          id: language.id,
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
            <>
              <span>前缀代码 {prefixLineCount(language.solutionPrefix)} 行</span>
              <time dateTime={language.updatedAt} className="w-12 text-right tabular-nums">
                {updatedAtFormat.format(new Date(language.updatedAt))}
              </time>
            </>
          ),
          meta: <span>前缀代码 {prefixLineCount(language.solutionPrefix)} 行</span>,
        })}
      />
    </ListPageTemplate>
  );
}
