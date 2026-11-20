import { useForm, useStore } from '@tanstack/react-form';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useBlocker } from '@tanstack/react-router';
import { useEffect, useRef, useState } from 'react';

import type { AdminProblem, ProblemDifficulty, ProblemSample } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';

import { getAdminProblem, problemKeys, updateProblem } from '../api/problems-api';
import type { ProblemLanguagesHandle } from './problem-languages-editor';
import type { EditableProblemSample } from './problem-sample-list';

type WorkbenchValues = {
  title: string;
  statementMarkdown: string;
  inputDescriptionMarkdown: string;
  outputDescriptionMarkdown: string;
  constraintsMarkdown: string;
  hintMarkdown: string;
  difficulty: ProblemDifficulty;
  tags: string[];
  samples: EditableProblemSample[];
  starterCode: string;
  /** CORE 的驱动代码；ACM 或尚未填写时为空串。 */
  judgeTemplate: string;
};

type SaveSubmission = {
  values: WorkbenchValues;
};

// 停止输入多久后自动保存。linear.app 是边写边存；这里留一点间隔，避免每个字都发一次请求。
const AUTOSAVE_DELAY_MS = 800;

function toFormValues(problem: AdminProblem): WorkbenchValues {
  const language = problem.allowedLanguages[0];
  if (!language) throw new Error('题目语言配置为空。');
  return {
    title: problem.title,
    statementMarkdown: problem.statementMarkdown,
    inputDescriptionMarkdown: problem.inputDescriptionMarkdown,
    outputDescriptionMarkdown: problem.outputDescriptionMarkdown,
    constraintsMarkdown: problem.constraintsMarkdown ?? '',
    hintMarkdown: problem.hintMarkdown ?? '',
    difficulty: problem.difficulty,
    tags: problem.tags,
    samples: problem.samples.map((sample) => ({
      input: sample.input,
      output: sample.output,
      explanationMarkdown: sample.explanationMarkdown ?? '',
    })),
    starterCode: language.starterCode,
    judgeTemplate: language.judgeTemplate ?? '',
  };
}

function toProblemSamples(samples: EditableProblemSample[]): ProblemSample[] {
  return samples.map((sample, index) => ({
    ordinal: index + 1,
    input: sample.input,
    output: sample.output,
    explanationMarkdown: sample.explanationMarkdown.trim() || null,
  }));
}

function sameWorkbenchValues(left: WorkbenchValues, right: WorkbenchValues): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}

function cloneWorkbenchValues(value: WorkbenchValues): WorkbenchValues {
  return {
    ...value,
    tags: [...value.tags],
    samples: value.samples.map((sample) => ({ ...sample })),
  };
}

// 题面和语言配置共享题目行版本。保存题面成功后，用返回的版本继续保存语言配置。
export function useProblemWorkbenchSave(problem: AdminProblem, hasReferenceSource: boolean) {
  const queryClient = useQueryClient();
  const [lastSavedAt, setLastSavedAt] = useState<Date>();
  const [languagesDirty, setLanguagesDirty] = useState(false);
  const [languageSaving, setLanguageSaving] = useState(false);
  const languageEditor = useRef<ProblemLanguagesHandle>(null);
  // 题目没有草稿：除归档外都可直接编辑，公开题目的修改立即对学生生效。
  const editable = problem.status === 'ACTIVE';
  const isPublic = problem.visibility === 'PUBLIC';
  const autosave = editable && !isPublic;

  // 表单初值只来自打开页面或主动加载最新版本。普通响应会规范化部分字段，写回会打断输入。
  const [defaultValues, setDefaultValues] = useState(() => toFormValues(problem));
  // 最近一次成功保存的内容；"有没有未保存修改"就是当前内容是否与它不同。
  const [savedValues, setSavedValues] = useState(defaultValues);
  const form = useForm({
    defaultValues,
    onSubmit: async ({ value }) => {
      // 标题为空时不保存；提示就在标题下方，不打断输入。
      if (!value.title.trim() || languageSaving) return;
      let rowVersion = problem.rowVersion;
      if (isDirty) {
        const saved = await save.mutateAsync({ values: cloneWorkbenchValues(value) });
        rowVersion = saved.rowVersion;
      }
      if (languagesDirty && languageEditor.current) {
        await languageEditor.current.save(rowVersion);
        setLastSavedAt(new Date());
      }
    },
  });

  const save = useMutation({
    mutationFn: ({ values: value }: SaveSubmission) =>
      updateProblem(problem.id, {
        slug: problem.slug,
        title: value.title.trim(),
        statementMarkdown: value.statementMarkdown,
        inputDescriptionMarkdown: value.inputDescriptionMarkdown,
        outputDescriptionMarkdown: value.outputDescriptionMarkdown,
        constraintsMarkdown: value.constraintsMarkdown.trim() || null,
        hintMarkdown: value.hintMarkdown.trim() || null,
        difficulty: value.difficulty,
        tags: [...new Set(value.tags.map((tag) => tag.trim()).filter(Boolean))],
        samples: toProblemSamples(value.samples),
        starterCode: problem.allowedLanguages[0]?.starterCode ?? '',
        // 驱动代码省略表示不改；ACM 题目不能带它。

        rowVersion: problem.rowVersion,
      }),
    onSuccess: async (saved, submitted) => {
      queryClient.setQueryData(problemKeys.adminProblem(problem.id), saved);
      // 记下发出去的内容，不改编辑区；保存期间又改过的部分自然仍算未保存，会接着保存。
      setSavedValues(submitted.values);
      setLastSavedAt(new Date());
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: problemKeys.admin }),
        queryClient.invalidateQueries({ queryKey: problemKeys.publishCheck(problem.id) }),
      ]);
    },
  });

  const formValues = useStore(form.store, (state) => state.values);
  const isDirty = !sameWorkbenchValues(formValues, savedValues);
  const titleMissing = !formValues.title.trim();

  const markChanged = () => {
    if (!save.isPending) save.reset();
  };

  // 自动保存：停止修改一会儿后提交；保存进行中再改，等这次完成后接着存最新内容。
  // 保存失败时停下，等管理员处理（再次修改会清掉错误并恢复自动保存）。
  useEffect(() => {
    if (!autosave || !isDirty || titleMissing || save.isPending || save.isError || languageSaving)
      return undefined;
    const timer = window.setTimeout(() => void form.handleSubmit(), AUTOSAVE_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [
    autosave,
    form,
    formValues,
    isDirty,
    save.isError,
    save.isPending,
    titleMissing,
    languageSaving,
  ]);

  const hasUnsavedEdits = isDirty || languagesDirty;
  const hasUnsavedContent = hasUnsavedEdits || hasReferenceSource;
  const blocker = useBlocker({
    shouldBlockFn: ({ current, next }) => current.pathname !== next.pathname && hasUnsavedContent,
    enableBeforeUnload: hasUnsavedContent,
    withResolver: true,
  });

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (
        editable &&
        hasUnsavedEdits &&
        !save.isPending &&
        !languageSaving &&
        (event.metaKey || event.ctrlKey) &&
        event.key.toLowerCase() === 's' &&
        !event.isComposing
      ) {
        event.preventDefault();
        void form.handleSubmit();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [editable, form, hasUnsavedEdits, save.isPending, languageSaving]);

  // 测试数据、标定和公开都以服务端已保存的内容为准，本地还有没存的修改时先等它存好。
  let unsavedReason: string | undefined;
  if (!editable) unsavedReason = '题目已归档，不能再修改。';
  else if (hasUnsavedEdits) {
    unsavedReason = autosave ? '正在保存修改，完成后即可继续。' : '先保存题目修改。';
  }

  const conflict =
    save.error instanceof ApiError &&
    (save.error.status === 409 || save.error.code?.includes('CONFLICT'));

  const reloadLatest = async () => {
    const latest = await getAdminProblem(problem.id);
    queryClient.setQueryData(problemKeys.adminProblem(problem.id), latest);
    const latestValues = toFormValues(latest);
    // reset 也会清除 touched，后续渲染必须提供相同的新初值。
    setDefaultValues(latestValues);
    form.reset(latestValues);
    setSavedValues(latestValues);
    save.reset();
  };

  const copyLocalDraft = async () => {
    await navigator.clipboard.writeText(JSON.stringify(formValues, null, 2));
  };

  return {
    form,
    title: formValues.title,
    titleMissing,
    editable,
    isPublic,
    autosave,
    hasUnsavedEdits,
    hasUnsavedContent,
    unsavedReason,
    isSaving: save.isPending || languageSaving,
    saveError: save.error,
    conflict,
    lastSavedAt,
    languageEditor,
    blockLanguageSave: isDirty || save.isPending,
    setLanguagesDirty,
    setLanguageSaving,
    blocker,
    markChanged,
    reloadLatest,
    copyLocalDraft,
  };
}
