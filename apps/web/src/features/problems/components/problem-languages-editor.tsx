import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type Ref, useEffect, useImperativeHandle, useState } from 'react';

import { Button } from '@/components/ui/button';
import { CodeBlockEditor } from '@/components/ui/code-block-editor';
import { SelectField } from '@/components/ui/select';
import {
  adminLanguageKeys,
  listLanguageSettings,
} from '@/features/admin-languages/api/admin-languages-api';
import { driverSkeleton, editorSyntax } from '@/features/languages/language';
import type { AdminProblem, LanguageCalibration, ProblemLanguageInput } from '@/generated/api';

import { languageLimitsQuery, problemKeys, replaceProblemLanguages } from '../api/problems-api';
import { apiErrorMessage, ConfirmationDialog } from './problem-workbench-dialogs';

function values(problem: AdminProblem): ProblemLanguageInput[] {
  return problem.allowedLanguages.map((language) => ({
    id: language.id,
    starterCode: language.starterCode,
    judgeTemplate: language.judgeTemplate ?? null,
  }));
}

export type ProblemLanguagesHandle = { save: (rowVersion: number) => Promise<AdminProblem> };

export function ProblemLanguagesEditor({
  problem,
  blocked,
  onDirtyChange,
  saveRef,
  onSavingChange,
  onCalibrate,
  calibration,
}: {
  problem: AdminProblem;
  blocked: boolean;
  onDirtyChange: (dirty: boolean) => void;
  saveRef: Ref<ProblemLanguagesHandle>;
  onSavingChange: (saving: boolean) => void;
  onCalibrate: (languageId: string) => void;
  calibration: LanguageCalibration | undefined;
}) {
  const client = useQueryClient();
  const catalog = useQuery({
    queryKey: adminLanguageKeys.all,
    queryFn: ({ signal }) => listLanguageSettings(signal),
  });
  const limits = useQuery(languageLimitsQuery(problem.id, problem.testData !== null));
  const [drafts, setDrafts] = useState(() => values(problem));
  const [saved, setSaved] = useState(() => values(problem));
  const [seen, setSeen] = useState(problem);
  const [selected, setSelected] = useState(problem.allowedLanguages[0]?.id ?? '');
  const [removing, setRemoving] = useState<string>();
  const [edited, setEdited] = useState<Record<string, string>>({});
  const dirty = JSON.stringify(drafts) !== JSON.stringify(saved);
  if (seen !== problem) {
    setSeen(problem);
    if (!dirty) {
      setDrafts(values(problem));
      setSaved(values(problem));
    }
  }
  const editable = problem.status === 'ACTIVE';
  const current = drafts.find((language) => language.id === selected) ?? drafts[0];
  const settings = catalog.data?.find((language) => language.id === current?.id);
  const known = problem.allowedLanguages.find((language) => language.id === current?.id);
  const displayName = settings?.displayName ?? known?.displayName ?? current?.id ?? '';
  const prefix = settings?.solutionPrefix ?? known?.solutionPrefix;
  const save = useMutation({
    mutationFn: (submitted: { languages: ProblemLanguageInput[]; rowVersion: number }) =>
      replaceProblemLanguages(problem.id, {
        rowVersion: submitted.rowVersion,
        allowedLanguages: submitted.languages,
      }),
    onSuccess: async (result, submitted) => {
      setSaved(submitted.languages);
      client.setQueryData(problemKeys.adminProblem(problem.id), result);
      await Promise.all([
        client.invalidateQueries({ queryKey: problemKeys.publishCheck(problem.id) }),
        client.invalidateQueries({ queryKey: problemKeys.limits(problem.id) }),
        client.invalidateQueries({ queryKey: problemKeys.readyLanguages(problem.slug) }),
      ]);
    },
  });
  useImperativeHandle(saveRef, () => ({
    save: (rowVersion) => save.mutateAsync({ languages: drafts, rowVersion }),
  }));
  useEffect(() => onSavingChange(save.isPending), [save.isPending, onSavingChange]);
  useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange]);
  useEffect(() => {
    if (
      !dirty ||
      blocked ||
      !editable ||
      problem.visibility === 'PUBLIC' ||
      save.isPending ||
      save.isError
    )
      return;
    const timer = window.setTimeout(
      () => save.mutate({ languages: drafts, rowVersion: problem.rowVersion }),
      800,
    );
    return () => window.clearTimeout(timer);
  }, [dirty, drafts, blocked, editable, problem.visibility, problem.rowVersion, save]);

  function change(field: 'starterCode' | 'judgeTemplate', value: string) {
    if (!current) return;
    save.reset();
    if (field === 'judgeTemplate')
      setEdited((previous) => ({ ...previous, [current.id]: calibration?.id ?? '' }));
    setDrafts((previous) =>
      previous.map((language) =>
        language.id === current.id
          ? { ...language, [field]: field === 'judgeTemplate' ? value || null : value }
          : language,
      ),
    );
  }

  return (
    <section className="mt-8 grid min-w-0 grid-cols-1 gap-6" aria-label="题目语言配置">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-foreground text-sm">语言配置</h2>
        <span className="text-fg-meta text-xs" role="status">
          {save.isPending ? '正在保存语言…' : dirty ? '语言配置未保存' : '语言配置已保存'}
        </span>
        <SelectField
          label="编辑语言"
          labelPlacement="hidden"
          value={current?.id ?? ''}
          items={drafts.map((language) => ({
            value: language.id,
            label:
              catalog.data?.find((item) => item.id === language.id)?.displayName ??
              problem.allowedLanguages.find((item) => item.id === language.id)?.displayName ??
              language.id,
          }))}
          onValueChange={setSelected}
        />
        {editable ? (
          <SelectField
            label="添加语言"
            labelPlacement="hidden"
            placeholder="添加语言"
            value=""
            disabled={catalog.isPending || catalog.isError || save.isPending || drafts.length >= 32}
            items={(catalog.data ?? [])
              .filter((item) => !drafts.some((language) => language.id === item.id))
              .map((item) => ({ value: item.id, label: item.displayName }))}
            onValueChange={(id) => {
              if (id && !drafts.some((item) => item.id === id)) {
                save.reset();
                setDrafts([...drafts, { id, starterCode: '', judgeTemplate: null }]);
                setSelected(id);
              }
            }}
          />
        ) : null}
        {editable ? (
          <Button
            type="button"
            size="sm"
            variant="secondary"
            disabled={blocked || !dirty}
            loading={save.isPending}
            onClick={() => save.mutate({ languages: drafts, rowVersion: problem.rowVersion })}
          >
            保存语言
          </Button>
        ) : null}
        {editable ? (
          <Button
            type="button"
            size="sm"
            variant="ghost"
            disabled={drafts.length <= 1 || save.isPending}
            onClick={() => setRemoving(current?.id)}
          >
            移除语言
          </Button>
        ) : null}
      </div>
      {blocked && dirty ? (
        <p className="text-fg-2 text-xs" role="status">
          先保存题面修改，再保存语言配置。
        </p>
      ) : null}
      {drafts.length === 1 ? <p className="text-fg-meta text-xs">至少保留一门语言。</p> : null}
      {catalog.isError ? (
        <p className="text-danger text-sm" role="alert">
          语言目录加载失败。
          <Button type="button" variant="ghost" size="sm" onClick={() => void catalog.refetch()}>
            重试
          </Button>
        </p>
      ) : null}
      {save.isError ? (
        <p className="text-danger text-sm" role="alert">
          {apiErrorMessage(save.error, '语言配置保存失败，修改仍保留在页面中。')}
        </p>
      ) : null}
      {current ? (
        <>
          <section aria-label="起始代码" className="grid min-w-0 gap-2">
            <h3 className="text-fg-2 text-sm">起始代码 · {displayName}</h3>
            <CodeBlockEditor
              language={editorSyntax(current.id)}
              value={current.starterCode}
              readOnly={!editable}
              aria-label={`${displayName} 起始代码`}
              onChange={(value) => change('starterCode', value)}
            />
          </section>
          {problem.codeMode === 'CORE' ? (
            <>
              {prefix ? (
                <section aria-label="前缀代码">
                  <p className="text-fg-meta text-xs">
                    用户文件 = 这 {prefix.split('\n').length - 1} 行 + 用户代码
                  </p>
                  <details open className="text-fg-meta min-w-0 text-xs">
                    <summary>前缀代码 · {displayName}</summary>
                    <pre className="overflow-x-auto font-mono">{prefix}</pre>
                  </details>
                </section>
              ) : null}
              <section aria-label="驱动代码" className="grid min-w-0 gap-2">
                <h3 className="text-fg-2 text-sm">驱动代码 · {displayName}</h3>
                {editable && !current.judgeTemplate?.trim() && driverSkeleton(current.id) ? (
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    onClick={() => change('judgeTemplate', driverSkeleton(current.id) ?? '')}
                  >
                    填入骨架
                  </Button>
                ) : null}
                {Object.hasOwn(edited, current.id) &&
                !(
                  calibration?.languageId === current.id &&
                  calibration.status === 'VALID' &&
                  calibration.id !== edited[current.id]
                ) ? (
                  <div className="flex flex-wrap items-center gap-2">
                    <span role="status" className="text-warning text-xs">
                      已修改，建议重新标定
                    </span>
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      onClick={() => onCalibrate(current.id)}
                    >
                      重新标定
                    </Button>
                  </div>
                ) : null}
                <CodeBlockEditor
                  language={editorSyntax(current.id)}
                  value={current.judgeTemplate ?? ''}
                  readOnly={!editable}
                  aria-label={`${displayName} 驱动代码`}
                  onChange={(value) => change('judgeTemplate', value)}
                />
              </section>
            </>
          ) : null}
        </>
      ) : null}
      {problem.testData ? (
        <div className="grid min-w-0 gap-2" aria-label="各语言运行限制">
          <h3 className="text-foreground text-sm">运行限制</h3>
          {limits.isPending ? <p className="text-fg-meta text-xs">正在读取限制…</p> : null}
          {limits.isError ? (
            <p className="text-danger text-xs" role="alert">
              {apiErrorMessage(limits.error, '限制读取失败。')}
              <Button type="button" size="sm" variant="ghost" onClick={() => void limits.refetch()}>
                重试限制
              </Button>
            </p>
          ) : null}
          {limits.data?.map((limit) => (
            <div
              key={limit.languageId}
              className="text-fg-2 flex flex-wrap items-baseline gap-3 text-xs"
            >
              <span className="w-20">
                {problem.allowedLanguages.find((item) => item.id === limit.languageId)
                  ?.displayName ?? limit.languageId}
              </span>
              <span>
                {limit.limitSource === 'BASE'
                  ? '基础标定'
                  : limit.limitSource === 'OVERRIDE'
                    ? '覆盖标定'
                    : limit.limitSource === 'DERIVED'
                      ? '公式推导'
                      : '尚无有效限制'}
              </span>
              {limit.cpuNs !== null && limit.memoryBytes !== null ? (
                <span className="font-mono">
                  {limit.cpuNs / 1_000_000} ms · {limit.memoryBytes / 1_048_576} MiB
                </span>
              ) : null}
              {limit.cpuCapped || limit.memoryCapped ? (
                <span className="text-warning">
                  {limit.cpuCapped ? '时间已截断 ' : ''}
                  {limit.memoryCapped ? '内存已截断' : ''}
                </span>
              ) : null}
              {limit.unavailableReason ? (
                <span>
                  {limit.unavailableReason === 'NO_CALIBRATION'
                    ? '请先完成基础或覆盖标定。'
                    : '语言限制参数不可用，请做覆盖标定或重新基础标定。'}
                </span>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}
      <ConfirmationDialog
        open={removing !== undefined}
        onOpenChange={(open) => {
          if (!open) setRemoving(undefined);
        }}
        title="移除这门语言？"
        description="保存后关闭这门语言的新作答入口，保留标定事实与历史提交；当前未保存的该语言代码会被移除。"
        confirmLabel="移除语言"
        onConfirm={() => {
          save.reset();
          setDrafts(drafts.filter((item) => item.id !== removing));
          setRemoving(undefined);
          return Promise.resolve();
        }}
      />
    </section>
  );
}
