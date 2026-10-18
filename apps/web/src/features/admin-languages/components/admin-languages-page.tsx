import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { useEffect, useId, useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import { CodeBlockEditor } from '@/components/ui/code-block-editor';
import { Cluster } from '@/components/ui/layout';
import { Heading, Text } from '@/components/ui/typography';
import { authKeys } from '@/features/auth/api/session-query';
import { ErrorNotice } from '@/features/auth/components/error-notice';
import { apiErrorMessage } from '@/features/problems/components/problem-workbench-dialogs';
import type { AdminLanguageSetting } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';

import {
  adminLanguageKeys,
  listLanguageSettings,
  updateLanguageSetting,
} from '../api/admin-languages-api';

const updatedAtFormat = new Intl.DateTimeFormat('zh-CN', {
  month: 'numeric',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** 前缀行数 = 换行个数：judging-service 换算报错行号用的是同一条规则。 */
function lineCount(prefix: string) {
  return prefix.split('\n').length - 1;
}

// 一门语言一节：标题行写语言名和现状，下面是前缀编辑框，改动后在框下出现影响说明与保存。
// 前缀对所有核心代码题立即生效，所以不自动保存。
function LanguagePrefixSection({ language }: { language: AdminLanguageSetting }) {
  const headingId = useId();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState(language.solutionPrefix);
  const [base, setBase] = useState(language.solutionPrefix);
  // 服务端的值变了（保存成功或重新加载）时，没有未保存改动的编辑框跟着更新。
  if (language.solutionPrefix !== base) {
    setBase(language.solutionPrefix);
    if (draft === base) setDraft(language.solutionPrefix);
  }
  const save = useMutation({
    mutationFn: (prefix: string) =>
      updateLanguageSetting(language.id, {
        solutionPrefix: prefix,
        expectedUpdatedAt: language.updatedAt,
      }),
    onSuccess: async (saved) => {
      setDraft(saved.solutionPrefix);
      await queryClient.invalidateQueries({ queryKey: adminLanguageKeys.all });
    },
  });
  const dirty = draft !== language.solutionPrefix;
  // 前缀必须以换行结尾；编辑框里末尾那个换行不显眼，保存时补上，而不是让人去猜为什么保存不了。
  const normalized = draft.endsWith('\n') ? draft : `${draft}\n`;
  const conflict = save.error instanceof ApiError && save.error.code === 'ROW_VERSION_CONFLICT';

  return (
    <section aria-labelledby={headingId} className="mt-8">
      <div className="mb-3 flex flex-wrap items-baseline gap-x-2">
        <h2 id={headingId} className="text-foreground font-heading text-15">
          {language.displayName}
        </h2>
        <Text as="span" size="sm" tone="meta">
          前缀代码 {lineCount(language.solutionPrefix)} 行 · 更新于{' '}
          <time dateTime={language.updatedAt}>
            {updatedAtFormat.format(new Date(language.updatedAt))}
          </time>
        </Text>
      </div>
      <CodeBlockEditor
        language={language.id === 'cpp' ? 'cpp' : 'plain'}
        value={draft}
        readOnly={save.isPending}
        aria-label={`${language.displayName} 前缀代码`}
        onChange={(value) => {
          setDraft(value);
          if (save.isError) save.reset();
        }}
      />
      {save.isError ? (
        <div className="mt-3">
          <ErrorNotice message={apiErrorMessage(save.error, '保存失败，请稍后重试。')} />
        </div>
      ) : null}
      {dirty || conflict ? (
        <Cluster gap={2} justify="between" className="mt-3">
          <Text size="sm" tone="meta" role="status">
            保存后立即用于之后的提交、自测与标定；已标定的核心代码题会提示重新标定。
          </Text>
          <Cluster gap={2}>
            {conflict ? (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  save.reset();
                  setDraft(language.solutionPrefix);
                  void queryClient.invalidateQueries({ queryKey: adminLanguageKeys.all });
                }}
              >
                放弃改动并重新加载
              </Button>
            ) : (
              <Button
                variant="secondary"
                size="sm"
                disabled={save.isPending}
                onClick={() => setDraft(language.solutionPrefix)}
              >
                还原
              </Button>
            )}
            <Button
              size="sm"
              disabled={!draft.trim() || conflict}
              loading={save.isPending}
              loadingLabel="正在保存…"
              onClick={() => save.mutate(normalized)}
            >
              保存
            </Button>
          </Cluster>
        </Cluster>
      ) : null}
    </section>
  );
}

export function AdminLanguagesPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const languages = useQuery({
    queryKey: adminLanguageKeys.all,
    queryFn: ({ signal }) => listLanguageSettings(signal),
  });
  const unauthenticated = languages.error instanceof ApiError && languages.error.status === 401;
  useEffect(() => {
    if (!unauthenticated) return;
    queryClient.setQueryData(authKeys.session(), { authenticated: false });
    void navigate({ to: '/login', search: { returnTo: '/admin/languages' } });
  }, [navigate, queryClient, unauthenticated]);

  return (
    <div className="mx-auto w-full max-w-[56rem] px-4 pt-6 pb-12 sm:px-14">
      <Heading level={1} size="xl" className="font-body text-2xl">
        语言
      </Heading>
      <Text size="sm" tone="meta" className="mt-2">
        每门语言一份前缀代码，所有核心代码题共用：用户文件 = 前缀代码 + 用户代码。前缀放常用的
        include / import，报错里前缀所在的行显示为“[预置代码]”。
      </Text>
      {languages.isPending ? (
        <div className="mt-6">
          <AsyncState
            variant="loading"
            size="inline"
            title="正在加载语言…"
            progressLabel="正在加载语言…"
          >
            {null}
          </AsyncState>
        </div>
      ) : languages.isError ? (
        <div className="mt-6">
          <AsyncState
            variant="error"
            size="inline"
            title="语言设置加载失败"
            action={
              <Button variant="secondary" onClick={() => void languages.refetch()}>
                重试
              </Button>
            }
          >
            {apiErrorMessage(languages.error, '请稍后重试。')}
          </AsyncState>
        </div>
      ) : (
        languages.data.map((language) => (
          <LanguagePrefixSection key={language.id} language={language} />
        ))
      )}
    </div>
  );
}
