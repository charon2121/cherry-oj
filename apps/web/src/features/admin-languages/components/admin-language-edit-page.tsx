import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useBlocker } from '@tanstack/react-router';
import { useEffect, useId, useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import { CodeBlockEditor } from '@/components/ui/code-block-editor';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { usePageCrumb } from '@/components/ui/header-slot';
import { Cluster } from '@/components/ui/layout';
import { DetailPageTemplate } from '@/components/ui/page-templates';
import { Text } from '@/components/ui/typography';
import { apiErrorMessage } from '@/features/problems/components/problem-workbench-dialogs';
import type { AdminLanguageSetting } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';

import {
  adminLanguageKeys,
  listLanguageSettings,
  updateLanguageSetting,
} from '../api/admin-languages-api';
import { prefixLineCount } from '../lib/prefix';
import { useRedirectWhenSignedOut } from './use-redirect-when-signed-out';

// 构图照 linear.app Settings › Templates 的模板编辑页：一列内容，区块标题 15px 加同一行的状态，
// 下面是编辑区，最下方右侧"还原 / 保存"。前缀对所有核心代码题立即生效，所以手动保存、保存前确认；
// 影响写在确认框里，页面上不常驻说明。返回由外壳面包屑（管理中心 › 语言 › C++）承担。
function PrefixEditor({ language }: { language: AdminLanguageSetting }) {
  const headingId = useId();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState(language.solutionPrefix);
  const [confirming, setConfirming] = useState(false);
  const dirty = draft !== language.solutionPrefix;
  // 末尾那个换行在编辑框里不显眼，保存时补上，而不是让人猜为什么保存不了。
  const normalized = draft.endsWith('\n') ? draft : `${draft}\n`;
  const save = useMutation({
    mutationFn: () =>
      updateLanguageSetting(language.id, {
        solutionPrefix: normalized,
        expectedUpdatedAt: language.updatedAt,
      }),
    onSuccess: (saved) => {
      setDraft(saved.solutionPrefix);
      queryClient.setQueryData<AdminLanguageSetting[]>(adminLanguageKeys.all, (items) =>
        items?.map((item) => (item.id === saved.id ? saved : item)),
      );
    },
    onSettled: () => setConfirming(false),
  });
  const conflict = save.error instanceof ApiError && save.error.code === 'ROW_VERSION_CONFLICT';
  const canSave = dirty && draft.trim().length > 0 && !conflict && !save.isPending;

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (
        (event.metaKey || event.ctrlKey) &&
        event.key.toLowerCase() === 's' &&
        !event.isComposing
      ) {
        event.preventDefault();
        if (canSave && !confirming) setConfirming(true);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [canSave, confirming]);

  const blocker = useBlocker({
    shouldBlockFn: ({ current, next }) => current.pathname !== next.pathname && dirty,
    enableBeforeUnload: dirty,
    withResolver: true,
  });

  const lines = prefixLineCount(dirty ? normalized : language.solutionPrefix);

  return (
    <section aria-labelledby={headingId}>
      <div className="mb-3 flex flex-wrap items-baseline gap-x-2">
        <h2 id={headingId} className="text-foreground font-heading text-15">
          前缀代码
        </h2>
        <Text as="span" size="sm" tone="meta" role="status">
          {lines} 行{dirty ? ' · 未保存' : ''}
        </Text>
      </div>
      <CodeBlockEditor
        language={language.id === 'cpp' ? 'cpp' : 'plain'}
        value={draft}
        readOnly={save.isPending}
        aria-label={`${language.displayName} 前缀代码`}
        onChange={(value) => {
          setDraft(value);
          if (save.isError && !conflict) save.reset();
        }}
      />
      {save.isError ? (
        <Text size="sm" className="text-danger mt-3" role="alert">
          {apiErrorMessage(save.error, '保存失败，请稍后重试。')}
        </Text>
      ) : null}
      <Cluster gap={2} justify="end" className="mt-4">
        {conflict ? (
          <Button
            variant="secondary"
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
            disabled={!dirty || save.isPending}
            onClick={() => setDraft(language.solutionPrefix)}
          >
            还原
          </Button>
        )}
        <Button
          disabled={!canSave}
          aria-keyshortcuts="Meta+S Control+S"
          title="保存（⌘S）"
          onClick={() => setConfirming(true)}
        >
          保存
        </Button>
      </Cluster>

      <Dialog open={confirming} onOpenChange={(open) => !save.isPending && setConfirming(open)}>
        <DialogContent size="sm">
          <DialogHeader>
            <DialogTitle>保存 {language.displayName} 前缀代码</DialogTitle>
            <DialogDescription>
              所有核心代码题之后的提交、自测与标定立即使用新前缀，已判完的提交不变。已标定的题目会在公开检查里提示重新标定。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose render={<Button variant="secondary" disabled={save.isPending} />}>
              取消
            </DialogClose>
            <Button loading={save.isPending} loadingLabel="正在保存…" onClick={() => save.mutate()}>
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={blocker.status === 'blocked'}>
        <DialogContent size="sm">
          <DialogHeader>
            <DialogTitle>离开前确认未保存内容</DialogTitle>
            <DialogDescription>前缀代码的修改尚未保存，离开后会丢失。</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="secondary" onClick={() => blocker.reset?.()}>
              留在此页
            </Button>
            <Button variant="danger" onClick={() => blocker.proceed?.()}>
              放弃并离开
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  );
}

export function AdminLanguageEditPage({ languageId }: { languageId: string }) {
  const languages = useQuery({
    queryKey: adminLanguageKeys.all,
    queryFn: ({ signal }) => listLanguageSettings(signal),
  });
  useRedirectWhenSignedOut(languages.error, `/admin/languages/${languageId}`);
  const language = languages.data?.find((item) => item.id === languageId);
  usePageCrumb(language?.displayName ?? languageId);

  const state = languages.isPending ? (
    <AsyncState variant="loading" size="inline" title="正在加载…" progressLabel="正在加载…">
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
  ) : language === undefined ? (
    <AsyncState variant="empty" size="inline" title="没有这门语言">
      {null}
    </AsyncState>
  ) : undefined;

  return (
    <DetailPageTemplate title={language?.displayName ?? '语言'} state={state} width="narrow">
      {language ? <PrefixEditor key={language.updatedAt} language={language} /> : null}
    </DetailPageTemplate>
  );
}
