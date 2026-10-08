import { useForm, useStore } from '@tanstack/react-form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useBlocker, useNavigate } from '@tanstack/react-router';
import {
  AlertTriangle,
  Archive,
  ArrowLeft,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Circle,
  Copy,
  FileCheck2,
  LoaderCircle,
  RotateCcw,
  Save,
  Trash2,
  XCircle,
} from 'lucide-react';
import {
  type ReactElement,
  type ReactNode,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Panel } from '@/components/ui/card';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { FormField } from '@/components/ui/field';
import { InlineNotice as UiInlineNotice } from '@/components/ui/inline-notice';
import { Input } from '@/components/ui/input';
import { Cluster, Container, Section, Stack } from '@/components/ui/layout';
import { SelectField } from '@/components/ui/select';
import { TextEditor } from '@/components/ui/text-editor';
import { Heading, Text } from '@/components/ui/typography';
import type { AdminProblem, ProblemDifficulty, ProblemSample } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';
import { cn } from '@/lib/utils';

import {
  archiveProblem,
  calibrate,
  deleteProblem,
  getAdminProblem,
  getPublishCheck,
  problemKeys,
  publish,
  replaceTestData,
  unpublish,
  updateProblem,
} from '../api/problems-api';
import { ProblemMarkdownEditor } from './problem-markdown-editor';
import { type EditableProblemSample, ProblemSampleList } from './problem-sample-list';
import { ProblemTagInput } from './problem-tag-input';

export type ProblemWorkbenchStep =
  'basic' | 'statement' | 'samples' | 'starter-code' | 'test-and-calibrate' | 'publish';

type StepStatus = 'not-started' | 'current' | 'complete' | 'needs-attention';

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
};

type SaveSubmission = {
  values: WorkbenchValues;
  editRevision: number;
};

const steps: ReadonlyArray<{ id: ProblemWorkbenchStep; label: string }> = [
  { id: 'basic', label: '基本信息' },
  { id: 'statement', label: '题面' },
  { id: 'samples', label: '样例' },
  { id: 'starter-code', label: '起始代码' },
  { id: 'test-and-calibrate', label: '测试与校准' },
  { id: 'publish', label: '检查与公开' },
];

const difficultyItems = [
  { value: 'UNRATED', label: '未评级' },
  { value: 'EASY', label: '简单' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '困难' },
];

function toFormValues(version: AdminProblem): WorkbenchValues {
  return {
    title: version.title,
    statementMarkdown: version.statementMarkdown,
    inputDescriptionMarkdown: version.inputDescriptionMarkdown,
    outputDescriptionMarkdown: version.outputDescriptionMarkdown,
    constraintsMarkdown: version.constraintsMarkdown ?? '',
    hintMarkdown: version.hintMarkdown ?? '',
    difficulty: version.difficulty,
    tags: version.tags,
    samples: version.samples.map((sample) => ({
      input: sample.input,
      output: sample.output,
      explanationMarkdown: sample.explanationMarkdown ?? '',
    })),
    starterCode: version.allowedLanguages[0].starterCode,
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

function formatBytes(value: number | null): string {
  if (value === null) return '大小未知';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`;
  return `${(value / 1024 / 1024).toFixed(1)} MiB`;
}

function secondsToNanoseconds(value: string, optional = false): number | null {
  if (optional && value.trim() === '') return null;
  const converted = Number(value) * 1_000_000_000;
  if (!Number.isFinite(converted) || converted <= 0 || !Number.isSafeInteger(converted)) {
    throw new Error('时间限制必须是可换算为安全整数的正数。');
  }
  return converted;
}

function mebibytesToBytes(value: string): number {
  const converted = Number(value) * 1_048_576;
  if (!Number.isFinite(converted) || converted <= 0 || !Number.isSafeInteger(converted)) {
    throw new Error('内存限制必须是可换算为安全整数的正数。');
  }
  return converted;
}

function apiErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ApiError)) return error instanceof Error ? error.message : fallback;
  const request = error.requestId ? `（请求 ID：${error.requestId}）` : '';
  return `${error.problem?.detail ?? error.message ?? fallback}${request}`;
}

function nanosecondsToSeconds(value: number | null): string | undefined {
  return value === null ? undefined : String(value / 1_000_000_000);
}

function bytesToMebibytes(value: number | null): string | undefined {
  return value === null ? undefined : String(value / 1_048_576);
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

export function AdminProblemWorkbench({
  problemId,
  step,
  onStepChange,
}: {
  problemId: string;
  step: ProblemWorkbenchStep;
  onStepChange: (step: ProblemWorkbenchStep) => void;
}) {
  const problem = useQuery({
    queryKey: problemKeys.adminProblem(problemId),
    queryFn: ({ signal }) => getAdminProblem(problemId, signal),
  });

  if (problem.isPending) {
    return (
      <Container>
        <Section>
          <AsyncState
            variant="loading"
            size="page"
            title="正在恢复题目工作台…"
            progressLabel="正在恢复题目工作台…"
          >
            {null}
          </AsyncState>
        </Section>
      </Container>
    );
  }

  if (problem.isError) {
    return (
      <Container>
        <Section>
          <AsyncState
            variant="error"
            size="page"
            title="工作台无法加载"
            action={
              <Button variant="secondary" onClick={() => void problem.refetch()}>
                <RotateCcw aria-hidden="true" />
                重新加载
              </Button>
            }
          >
            {apiErrorMessage(problem.error, '没有覆盖本地数据，请检查权限或链接。')}
          </AsyncState>
        </Section>
      </Container>
    );
  }

  return <WorkbenchEditor problem={problem.data} step={step} onStepChange={onStepChange} />;
}

function WorkbenchEditor({
  problem,
  step,
  onStepChange,
}: {
  problem: AdminProblem;
  step: ProblemWorkbenchStep;
  onStepChange: (step: ProblemWorkbenchStep) => void;
}) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const uploadController = useRef<AbortController | undefined>(undefined);
  const editRevision = useRef(0);
  const stepContentRef = useRef<HTMLDivElement>(null);
  const previousStepRef = useRef(step);
  const [lastSavedAt, setLastSavedAt] = useState<Date>();
  const [formError, setFormError] = useState<string>();
  const [referenceSource, setReferenceSource] = useState('');
  const [uploadState, setUploadState] = useState<string>();
  const [limits, setLimits] = useState({ cpuSeconds: '1', memoryMib: '256', clockSeconds: '' });
  // 题目没有草稿：除归档外都可直接编辑，公开题目的修改立即对学生生效。
  const editable = problem.status === 'ACTIVE';

  useEffect(
    () => () => {
      uploadController.current?.abort();
    },
    [],
  );

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: problemKeys.adminProblem(problem.id) }),
      queryClient.invalidateQueries({ queryKey: problemKeys.admin }),
      queryClient.invalidateQueries({ queryKey: problemKeys.publishCheck(problem.id) }),
    ]);
  };

  const form = useForm({
    defaultValues: toFormValues(problem),
    onSubmit: async ({ value }) => {
      setFormError(undefined);
      if (!value.title.trim()) {
        setFormError('请先填写题目标题。');
        onStepChange('basic');
        return;
      }
      await save.mutateAsync({
        values: cloneWorkbenchValues(value),
        editRevision: editRevision.current,
      });
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
        starterCode: value.starterCode,
        rowVersion: problem.rowVersion,
      }),
    onSuccess: async (saved, submitted) => {
      queryClient.setQueryData(problemKeys.adminProblem(problem.id), saved);
      if (
        editRevision.current === submitted.editRevision &&
        sameWorkbenchValues(form.state.values, submitted.values)
      ) {
        form.reset(toFormValues(saved));
      }
      setLastSavedAt(new Date());
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: problemKeys.admin }),
        queryClient.invalidateQueries({ queryKey: problemKeys.publishCheck(problem.id) }),
      ]);
    },
  });

  const isDirty = useStore(form.store, (state) => state.isDirty);
  const formValues = useStore(form.store, (state) => state.values);

  const markChanged = () => {
    editRevision.current += 1;
    if (!save.isPending) save.reset();
  };

  const hasUnsavedContent = isDirty || referenceSource.trim().length > 0;
  const blocker = useBlocker({
    shouldBlockFn: ({ current, next }) => current.pathname !== next.pathname && hasUnsavedContent,
    enableBeforeUnload: hasUnsavedContent,
    withResolver: true,
  });

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (
        editable &&
        isDirty &&
        !save.isPending &&
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
  }, [editable, form, isDirty, save.isPending]);

  const upload = useMutation({
    mutationFn: ({ file, signal }: { file: File; signal: AbortSignal }) =>
      replaceTestData(problem.id, file, signal),
    onMutate: () => setUploadState('正在上传并检查 ZIP…'),
    onSuccess: async (uploaded) => {
      uploadController.current = undefined;
      setUploadState(
        `已替换测试数据，共 ${uploaded.testcaseCount} 组。旧的校准已过期，请重新校准。`,
      );
      await refresh();
    },
    onError: (error) => {
      uploadController.current = undefined;
      setUploadState(
        error instanceof ApiError && error.kind === 'aborted'
          ? '上传已取消。'
          : apiErrorMessage(error, '上传或 ZIP 检查失败；原有测试数据保持不变。'),
      );
    },
  });

  const calibration = useMutation({
    mutationFn: () => {
      const cpuNs = secondsToNanoseconds(limits.cpuSeconds);
      const clockNs = secondsToNanoseconds(limits.clockSeconds, true);
      if (cpuNs === null) throw new Error('请填写 CPU 时间限制。');
      return calibrate(problem.id, {
        languageId: 'cpp',
        cpuNs,
        memoryBytes: mebibytesToBytes(limits.memoryMib),
        clockNs,
        referenceSource,
      });
    },
    onSuccess: async (result) => {
      const cpuSeconds = nanosecondsToSeconds(result.cpuNs);
      const memoryMib = bytesToMebibytes(result.memoryBytes);
      const clockSeconds = nanosecondsToSeconds(result.clockNs);
      setLimits((current) => ({
        cpuSeconds: cpuSeconds ?? current.cpuSeconds,
        memoryMib: memoryMib ?? current.memoryMib,
        clockSeconds: clockSeconds ?? '',
      }));
      setReferenceSource('');
      await refresh();
    },
  });

  const check = useQuery({
    queryKey: problemKeys.publishCheck(problem.id),
    queryFn: ({ signal }) => getPublishCheck(problem.id, signal),
    refetchInterval: 10_000,
    retry: false,
  });

  const publishing = useMutation({
    mutationFn: () => publish(problem.id, problem.rowVersion),
    onSuccess: refresh,
  });
  const unpublishing = useMutation({
    mutationFn: () => unpublish(problem.id, problem.rowVersion),
    onSuccess: refresh,
  });
  const archive = useMutation({
    mutationFn: () => archiveProblem(problem.id, problem.rowVersion),
    onSuccess: refresh,
  });
  const remove = useMutation({
    mutationFn: () => deleteProblem(problem.id, problem.rowVersion),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: problemKeys.admin });
      await navigate({ to: '/admin/problems', search: { page: 1, q: '', status: 'ALL' } });
    },
  });

  const nodeUnavailable = check.data?.checks.find(
    (item) => item.code === 'ONLINE_JUDGE_NODE' && !item.passed,
  );
  const calibrationReady = check.data?.checks.some(
    (item) => item.code === 'CALIBRATION' && item.passed,
  );
  const canUseTestActions = editable && !isDirty;
  const isPublic = problem.visibility === 'PUBLIC';

  const derivedStepStatuses = useMemo<Record<ProblemWorkbenchStep, StepStatus>>(
    () => ({
      basic:
        !formValues.title.trim() || formValues.difficulty === 'UNRATED'
          ? 'needs-attention'
          : 'complete',
      statement:
        formValues.statementMarkdown.trim() &&
        formValues.inputDescriptionMarkdown.trim() &&
        formValues.outputDescriptionMarkdown.trim()
          ? 'complete'
          : 'needs-attention',
      samples:
        formValues.samples.length > 0 &&
        formValues.samples.every((sample) => sample.input.trim() && sample.output.trim())
          ? 'complete'
          : 'needs-attention',
      'starter-code': formValues.starterCode.trim() ? 'complete' : 'not-started',
      'test-and-calibrate': calibrationReady
        ? 'complete'
        : problem.testData
          ? 'needs-attention'
          : 'not-started',
      publish: check.data?.ready ? 'complete' : 'needs-attention',
    }),
    [calibrationReady, check.data?.ready, formValues, problem.testData],
  );
  const stepStatuses: Record<ProblemWorkbenchStep, StepStatus> = {
    ...derivedStepStatuses,
    [step]: 'current',
  };

  const conflict =
    save.error instanceof ApiError &&
    (save.error.status === 409 || save.error.code?.includes('CONFLICT'));

  const reloadLatest = async () => {
    const latest = await getAdminProblem(problem.id);
    queryClient.setQueryData(problemKeys.adminProblem(problem.id), latest);
    form.reset(toFormValues(latest));
    editRevision.current = 0;
    setFormError(undefined);
    save.reset();
  };

  const copyLocalDraft = async () => {
    await navigator.clipboard.writeText(JSON.stringify(formValues, null, 2));
  };

  const currentStepIndex = steps.findIndex((item) => item.id === step);
  const setStep = (next: ProblemWorkbenchStep) => onStepChange(next);

  useLayoutEffect(() => {
    if (previousStepRef.current === step) return;
    previousStepRef.current = step;
    stepContentRef.current?.scrollIntoView({ block: 'start' });
  }, [step]);

  return (
    <Container width="wide">
      <div className="top-header -mx-gutter-phone border-border-soft bg-panel px-gutter-phone sm:-mx-gutter-tablet sm:px-gutter-tablet lg:-mx-gutter-desktop lg:px-gutter-desktop sticky z-20 border-b md:top-0">
        <div className="min-h-header flex min-w-0 flex-wrap items-center justify-between gap-3 py-2">
          <div className="flex min-w-0 items-center gap-3">
            <Link
              to="/admin/problems"
              search={{ page: 1, q: '', status: 'ALL' }}
              className={buttonVariants({ size: 'sm', variant: 'toolbar' })}
              aria-label="返回题目管理"
            >
              <ArrowLeft aria-hidden="true" />
              <span className="hidden sm:inline">题目管理</span>
            </Link>
            <div className="min-w-0">
              <div className="flex min-w-0 flex-wrap items-center gap-2">
                <Heading level={1} size="sm" className="truncate">
                  {formValues.title || problem.title}
                </Heading>
                <Badge>{problem.status}</Badge>
                <Badge>{problem.visibility}</Badge>
              </div>
              <Text size="cap" tone="meta">
                {problem.slug} · ACM / C++
              </Text>
            </div>
          </div>
          <div className="flex min-w-0 flex-wrap items-center justify-end gap-2">
            <SaveStatus
              isDirty={isDirty}
              isSaving={save.isPending}
              isError={save.isError}
              hasReferenceSource={Boolean(referenceSource.trim())}
              lastSavedAt={lastSavedAt}
            />
            <Button
              size="sm"
              variant="secondary"
              disabled={!editable || !isDirty}
              loading={save.isPending}
              onClick={() => void form.handleSubmit()}
            >
              <Save aria-hidden="true" />
              保存
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setStep('publish')}>
              <FileCheck2 aria-hidden="true" />
              检查与公开
            </Button>
          </div>
        </div>
      </div>

      {!editable ? (
        <UiInlineNotice className="mt-4" variant="info" title="这道题已归档，当前为只读状态">
          归档的题目不能再修改。
        </UiInlineNotice>
      ) : isPublic ? (
        <UiInlineNotice className="mt-4" variant="warning" title="这道题已公开">
          保存会立即对学生生效；公开题目不能被改成空题面或没有样例。
        </UiInlineNotice>
      ) : null}

      {save.isError ? (
        <Panel className="mt-4" role="alert">
          <Heading level={2} size="lg">
            {conflict ? '服务端已有更新，本地内容仍保留' : '保存失败'}
          </Heading>
          <Text className="text-danger mt-2" size="sm">
            {conflict
              ? '先复制本地内容，再加载服务端最新版本并手动合并；不会自动覆盖。'
              : apiErrorMessage(save.error, '请检查内容后重试。')}
          </Text>
          <Cluster className="mt-3" gap={2}>
            {conflict ? (
              <Button variant="secondary" onClick={() => void copyLocalDraft()}>
                <Copy aria-hidden="true" />
                复制本地内容
              </Button>
            ) : null}
            <Button variant="secondary" onClick={() => void reloadLatest()}>
              <RotateCcw aria-hidden="true" />
              加载服务端最新版本
            </Button>
          </Cluster>
        </Panel>
      ) : null}

      {formError ? (
        <Text className="text-danger mt-4" role="alert">
          {formError}
        </Text>
      ) : null}

      <div className="mt-5 lg:grid lg:grid-cols-[var(--layout-sidebar)_minmax(0,1fr)] lg:items-start lg:gap-6">
        <WorkbenchNavigation current={step} statuses={stepStatuses} onChange={setStep} />

        <div
          ref={stepContentRef}
          role="region"
          aria-label={`${steps[currentStepIndex]?.label ?? '题目'}编辑`}
          className="scroll-mt-[calc(var(--layout-header)+var(--layout-header)+var(--space-6))] md:scroll-mt-[calc(var(--layout-header)+var(--space-6))]"
        >
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void form.handleSubmit();
            }}
          >
            {step === 'basic' ? (
              <Stack gap={6}>
                <div className="grid gap-5 md:grid-cols-2">
                  <form.Field name="title">
                    {(field) => (
                      <FormField label="题目标题" required>
                        <Input
                          value={field.state.value}
                          disabled={!editable}
                          onChange={(event) => {
                            field.handleChange(event.target.value);
                            markChanged();
                          }}
                        />
                      </FormField>
                    )}
                  </form.Field>
                  <FormField label="题目标识" description="创建后保持稳定，用于公开访问地址。">
                    <Input value={problem.slug} readOnly />
                  </FormField>
                  <form.Field name="difficulty">
                    {(field) => (
                      <SelectField
                        label="难度"
                        value={field.state.value}
                        disabled={!editable}
                        onValueChange={(value) => {
                          field.handleChange(value as ProblemDifficulty);
                          markChanged();
                        }}
                        items={difficultyItems}
                      />
                    )}
                  </form.Field>
                  <FormField label="判题模式与语言">
                    <Input value="ACM / C++" readOnly />
                  </FormField>
                </div>
                <form.Field name="tags">
                  {(field) => (
                    <FormField label="标签" description="输入后按 Enter 或逗号添加；最多 20 个。">
                      <ProblemTagInput
                        value={field.state.value}
                        disabled={!editable}
                        onChange={(value) => {
                          field.handleChange(value);
                          markChanged();
                        }}
                      />
                    </FormField>
                  )}
                </form.Field>
              </Stack>
            ) : null}

            {step === 'statement' ? (
              <Stack gap={6}>
                <form.Field name="statementMarkdown">
                  {(field) => (
                    <ProblemMarkdownEditor
                      label="题目正文"
                      value={field.state.value}
                      disabled={!editable}
                      required
                      size="default"
                      description="支持 Markdown；预览会经过与用户端一致的安全处理。"
                      onChange={(value) => {
                        field.handleChange(value);
                        markChanged();
                      }}
                    />
                  )}
                </form.Field>
                <div className="grid gap-6 xl:grid-cols-2">
                  <form.Field name="inputDescriptionMarkdown">
                    {(field) => (
                      <ProblemMarkdownEditor
                        label="输入说明"
                        value={field.state.value}
                        disabled={!editable}
                        required
                        onChange={(value) => {
                          field.handleChange(value);
                          markChanged();
                        }}
                      />
                    )}
                  </form.Field>
                  <form.Field name="outputDescriptionMarkdown">
                    {(field) => (
                      <ProblemMarkdownEditor
                        label="输出说明"
                        value={field.state.value}
                        disabled={!editable}
                        required
                        onChange={(value) => {
                          field.handleChange(value);
                          markChanged();
                        }}
                      />
                    )}
                  </form.Field>
                  <form.Field name="constraintsMarkdown">
                    {(field) => (
                      <ProblemMarkdownEditor
                        label="数据范围"
                        value={field.state.value}
                        disabled={!editable}
                        onChange={(value) => {
                          field.handleChange(value);
                          markChanged();
                        }}
                      />
                    )}
                  </form.Field>
                  <form.Field name="hintMarkdown">
                    {(field) => (
                      <ProblemMarkdownEditor
                        label="提示"
                        value={field.state.value}
                        disabled={!editable}
                        onChange={(value) => {
                          field.handleChange(value);
                          markChanged();
                        }}
                      />
                    )}
                  </form.Field>
                </div>
              </Stack>
            ) : null}

            {step === 'samples' ? (
              <Stack gap={6}>
                <form.Field name="samples">
                  {(field) => (
                    <ProblemSampleList
                      value={field.state.value}
                      disabled={!editable}
                      onChange={(value) => {
                        field.handleChange(value);
                        markChanged();
                      }}
                    />
                  )}
                </form.Field>
              </Stack>
            ) : null}

            {step === 'starter-code' ? (
              <Stack gap={6}>
                <form.Field name="starterCode">
                  {(field) => (
                    <FormField
                      label="C++ 起始代码"
                      description="留空时答题者会得到空白编辑器。Tab 可离开编辑器，使用常规快捷键搜索和撤销。"
                    >
                      <TextEditor
                        language="cpp"
                        size="code"
                        value={field.state.value}
                        readOnly={!editable}
                        aria-label="C++ 起始代码编辑器"
                        onChange={(value) => {
                          field.handleChange(value);
                          markChanged();
                        }}
                      />
                    </FormField>
                  )}
                </form.Field>
              </Stack>
            ) : null}
          </form>

          {step === 'test-and-calibrate' ? (
            <Stack gap={6}>
              {isDirty ? (
                <UiInlineNotice variant="warning" title="先保存">
                  再上传测试数据或校准，避免使用过期内容。
                </UiInlineNotice>
              ) : null}
              <ProcessSection
                number={1}
                title="上传测试数据"
                status={problem.testData ? '可用' : '未开始'}
              >
                <div className="grid gap-3 sm:grid-cols-[minmax(14rem,1fr)_auto]">
                  <FormField
                    label="测试数据 ZIP"
                    description="文件名需安全，.in/.out 必须成对。题目只有一份测试数据：上传即替换，替换后旧的校准过期。"
                  >
                    <Input
                      type="file"
                      accept=".zip,application/zip"
                      disabled={!canUseTestActions || upload.isPending}
                      onChange={(event) => {
                        const file = event.target.files?.[0];
                        if (!file) return;
                        uploadController.current?.abort();
                        const controller = new AbortController();
                        uploadController.current = controller;
                        upload.mutate({ file, signal: controller.signal });
                        event.currentTarget.value = '';
                      }}
                    />
                  </FormField>
                  <Button
                    className="self-end"
                    variant="secondary"
                    disabled={!upload.isPending}
                    onClick={() => uploadController.current?.abort()}
                  >
                    取消上传
                  </Button>
                </div>
                {uploadState ? (
                  <Text className="mt-3" role="status" aria-live="polite">
                    {uploadState}
                  </Text>
                ) : null}
                {upload.isPending ? (
                  <progress className="mt-3 w-full" aria-label="测试数据正在上传并检查" />
                ) : null}
                {problem.testData ? (
                  <div className="border-border bg-surface-translucent mt-4 min-w-0 rounded-sm border p-3">
                    <strong>当前测试数据</strong> · {problem.testData.testcaseCount} 组 ·{' '}
                    {formatBytes(problem.testData.totalBytes)}
                    <details className="text-muted-foreground mt-1 text-sm">
                      <summary>技术详情</summary>
                      <span className="font-mono wrap-anywhere">{problem.testData.digest}</span>
                    </details>
                  </div>
                ) : (
                  <Text className="mt-4" size="sm" tone="muted">
                    还没有测试数据。
                  </Text>
                )}
              </ProcessSection>

              <ProcessSection
                number={2}
                title="运行参考程序校准"
                status={calibrationReady ? '可用' : '未完成'}
              >
                <div className="grid gap-4 md:grid-cols-3">
                  <FormField label="CPU 时间（秒）">
                    <Input
                      inputMode="decimal"
                      value={limits.cpuSeconds}
                      disabled={!editable}
                      onChange={(event) =>
                        setLimits((current) => ({ ...current, cpuSeconds: event.target.value }))
                      }
                    />
                  </FormField>
                  <FormField label="内存（MiB）">
                    <Input
                      inputMode="decimal"
                      value={limits.memoryMib}
                      disabled={!editable}
                      onChange={(event) =>
                        setLimits((current) => ({ ...current, memoryMib: event.target.value }))
                      }
                    />
                  </FormField>
                  <FormField label="墙钟时间（秒）" description="可选。">
                    <Input
                      inputMode="decimal"
                      value={limits.clockSeconds}
                      disabled={!editable}
                      onChange={(event) =>
                        setLimits((current) => ({ ...current, clockSeconds: event.target.value }))
                      }
                    />
                  </FormField>
                </div>
                <Text className="mt-3" size="sm" tone="muted">
                  提交时会换算为判题系统使用的纳秒和字节，不需要手工计算。校准针对当前测试数据；数据更新后需要重新校准。
                </Text>
                <div className="mt-4">
                  <FormField
                    label="C++ 参考程序"
                    description="只保存在当前页面内存；校准成功或离开工作台后清空。"
                  >
                    <TextEditor
                      language="cpp"
                      size="code"
                      value={referenceSource}
                      readOnly={!editable}
                      aria-label="C++ 参考程序编辑器"
                      onChange={setReferenceSource}
                    />
                  </FormField>
                </div>
                <Button
                  className="mt-4"
                  loading={calibration.isPending}
                  disabled={
                    !canUseTestActions ||
                    !problem.testData ||
                    !!nodeUnavailable ||
                    !referenceSource.trim()
                  }
                  onClick={() => calibration.mutate()}
                >
                  运行参考程序校准
                </Button>
                {!problem.testData ? (
                  <Text className="mt-2" size="sm" tone="muted">
                    先上传测试数据。
                  </Text>
                ) : nodeUnavailable ? (
                  <Text className="mt-2" size="sm" tone="muted" role="status">
                    {nodeUnavailable.message}
                  </Text>
                ) : null}
                {calibration.data ? (
                  <Text className="mt-3" role="status">
                    校准状态：{calibration.data.status}。参考源码已从页面内存清除。
                  </Text>
                ) : null}
                {calibration.isError ? (
                  <Text className="text-danger mt-3" role="alert">
                    {apiErrorMessage(calibration.error, '校准失败，参考程序仍保留在当前页面。')}
                  </Text>
                ) : null}
              </ProcessSection>
            </Stack>
          ) : null}

          {step === 'publish' ? (
            <Stack gap={6}>
              <div className="border-border-soft flex flex-wrap items-center justify-between gap-3 border-b pb-4">
                <div>
                  <Text weight="medium">
                    {check.data?.ready ? '已满足公开条件' : '仍有项目需要处理'}
                  </Text>
                  <Text size="sm" tone="muted">
                    服务端检查是最终公开依据。
                  </Text>
                </div>
                <Button
                  variant="secondary"
                  loading={check.isFetching}
                  onClick={() => void check.refetch()}
                >
                  <RotateCcw aria-hidden="true" />
                  重新检查
                </Button>
              </div>
              {check.isError ? (
                <Text className="text-danger" role="alert">
                  {apiErrorMessage(check.error, '公开检查失败，请刷新题目状态。')}
                </Text>
              ) : null}
              <div className="grid gap-2">
                {check.isPending ? (
                  <AsyncState
                    variant="loading"
                    size="inline"
                    title="正在运行公开检查…"
                    progressLabel="正在运行公开检查…"
                  >
                    {null}
                  </AsyncState>
                ) : null}
                {check.data?.checks.map((item) => {
                  const target = publishCheckStep(item.code);
                  return (
                    <div
                      key={item.code}
                      className="border-border bg-surface-translucent flex min-w-0 flex-wrap items-center gap-3 rounded-sm border p-3"
                    >
                      {item.passed ? (
                        <CheckCircle2 className="text-success size-5" aria-hidden="true" />
                      ) : (
                        <XCircle className="text-danger size-5" aria-hidden="true" />
                      )}
                      <span className="min-w-48 flex-1">{item.message}</span>
                      {!item.passed ? (
                        <Button size="sm" variant="ghost" onClick={() => setStep(target)}>
                          去完善
                          <ChevronRight aria-hidden="true" />
                        </Button>
                      ) : null}
                    </div>
                  );
                })}
              </div>
              <div className="border-border-soft flex flex-wrap items-center justify-between gap-3 border-b pb-6">
                {isPublic ? (
                  <>
                    <Text size="sm" tone="muted">
                      题目已公开。取消公开后变回私有，已有提交记录保留。
                    </Text>
                    <ConfirmationDialog
                      title={`取消公开 ${problem.slug}`}
                      description="确认后题目从公开题库消失，学生不能再提交。"
                      confirmLabel="取消公开"
                      loading={unpublishing.isPending}
                      disabled={isDirty || !editable}
                      trigger={
                        <Button variant="secondary" disabled={isDirty || !editable}>
                          取消公开
                        </Button>
                      }
                      onConfirm={() => unpublishing.mutateAsync().then(() => undefined)}
                    />
                  </>
                ) : (
                  <>
                    <Text size="sm" tone="muted">
                      公开后学生立即可见；之后的修改也会立即生效。
                    </Text>
                    <ConfirmationDialog
                      title={`公开 ${problem.slug}`}
                      description="确认后，这道题进入公开题库。检查之后测试数据被替换则公开失败。"
                      confirmLabel="公开题目"
                      loading={publishing.isPending}
                      disabled={!check.data?.ready || isDirty || !editable}
                      trigger={
                        <Button disabled={!check.data?.ready || isDirty || !editable}>
                          公开题目
                        </Button>
                      }
                      onConfirm={() => publishing.mutateAsync().then(() => undefined)}
                    />
                  </>
                )}
              </div>
              {isDirty ? (
                <Text size="sm" tone="muted">
                  先保存，再公开或取消公开。
                </Text>
              ) : null}
              {publishing.isError || unpublishing.isError ? (
                <Text className="text-danger" role="alert">
                  {apiErrorMessage(
                    publishing.error ?? unpublishing.error,
                    '操作失败，题目状态保持不变。',
                  )}
                </Text>
              ) : null}

              <section
                className="bg-danger-soft border-danger-border grid gap-4 rounded-sm border p-4"
                aria-labelledby="danger-zone-heading"
              >
                <div>
                  <Heading id="danger-zone-heading" level={2} size="lg">
                    危险操作
                  </Heading>
                  <Text className="mt-1" size="sm" tone="muted">
                    这些动作会影响整道题。
                  </Text>
                </div>
                <div className="flex flex-wrap gap-2">
                  <ConfirmationDialog
                    title={`归档题目 ${problem.slug}`}
                    description="归档会让整道题退出正常管理流程，之后不能再修改。已公开的题目要先取消公开。"
                    confirmLabel="归档题目"
                    danger
                    loading={archive.isPending}
                    disabled={hasUnsavedContent || problem.status === 'ARCHIVED' || isPublic}
                    trigger={
                      <Button variant="danger">
                        <Archive aria-hidden="true" />
                        归档题目
                      </Button>
                    }
                    onConfirm={() => archive.mutateAsync().then(() => undefined)}
                  />
                  <ConfirmationDialog
                    title={`删除题目 ${problem.slug}`}
                    description="这个动作不可恢复，题面、样例和测试数据一并删除。只有从未公开过的题目允许删除，公开过的只能归档。"
                    confirmLabel="永久删除题目"
                    danger
                    loading={remove.isPending}
                    disabled={hasUnsavedContent || problem.publishedAt !== null}
                    trigger={
                      <Button variant="danger">
                        <Trash2 aria-hidden="true" />
                        删除题目
                      </Button>
                    }
                    onConfirm={() => remove.mutateAsync().then(() => undefined)}
                  />
                </div>
              </section>
            </Stack>
          ) : null}

          <div className="border-border-soft mt-8 flex flex-wrap items-center justify-between gap-3 border-t py-5">
            <Button
              variant="secondary"
              disabled={currentStepIndex <= 0}
              onClick={() => {
                const previous = steps[currentStepIndex - 1];
                if (previous) setStep(previous.id);
              }}
            >
              <ChevronLeft aria-hidden="true" />
              上一步
            </Button>
            <Text size="sm" tone="muted">
              {currentStepIndex + 1} / {steps.length} · 切换步骤不会自动保存
            </Text>
            <Button
              variant="secondary"
              disabled={currentStepIndex >= steps.length - 1}
              onClick={() => {
                const next = steps[currentStepIndex + 1];
                if (next) setStep(next.id);
              }}
            >
              下一步
              <ChevronRight aria-hidden="true" />
            </Button>
          </div>
        </div>
      </div>

      <Dialog open={blocker.status === 'blocked'}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>离开前确认未提交内容</DialogTitle>
            <DialogDescription>
              {isDirty ? '题目修改尚未保存。' : ''}
              {referenceSource.trim() ? '参考程序只在当前页面内存中，离开后会丢失。' : ''}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="secondary" onClick={() => blocker.reset?.()}>
              留在工作台
            </Button>
            <Button variant="danger" onClick={() => blocker.proceed?.()}>
              放弃并离开
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Container>
  );
}

function WorkbenchNavigation({
  current,
  statuses,
  onChange,
}: {
  current: ProblemWorkbenchStep;
  statuses: Record<ProblemWorkbenchStep, StepStatus>;
  onChange: (step: ProblemWorkbenchStep) => void;
}) {
  return (
    <nav
      aria-label="题目编辑步骤"
      className="mb-6 lg:sticky lg:top-[calc(var(--layout-header)+var(--space-6))] lg:mb-0"
    >
      <div className="sm:hidden">
        <SelectField
          label="当前步骤"
          value={current}
          onValueChange={(value) => onChange(value as ProblemWorkbenchStep)}
          items={steps.map((item) => ({
            value: item.id,
            label: `${item.label} · ${stepStatusLabel(statuses[item.id])}`,
          }))}
        />
      </div>
      <ol className="hidden gap-1 overflow-x-auto pb-2 sm:flex lg:grid">
        {steps.map((item, index) => {
          const status = statuses[item.id];
          return (
            <li key={item.id} className="min-w-40 lg:min-w-0">
              <button
                type="button"
                aria-current={current === item.id ? 'step' : undefined}
                className={cn(
                  'focus-visible:outline-ring hover:text-foreground text-fg-2 duration-fast hover:bg-surface-translucent-hover flex min-h-8 w-full items-center gap-2 rounded-xs border border-transparent px-2 py-1 text-left transition-colors focus-visible:outline-1 focus-visible:outline-offset-0 motion-reduce:transition-none',
                  current === item.id && 'text-foreground bg-surface-translucent-selected',
                )}
                onClick={() => onChange(item.id)}
              >
                <StepStatusIcon status={status} />
                <span className="min-w-0">
                  <span className="text-cap font-body block">
                    {index + 1}. {item.label}
                  </span>
                  <span className="text-fg-meta block text-xs">{stepStatusLabel(status)}</span>
                </span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

function StepStatusIcon({ status }: { status: StepStatus }) {
  if (status === 'complete') return <Check className="text-success size-4" aria-hidden="true" />;
  if (status === 'needs-attention') {
    return <AlertTriangle className="text-warning size-4" aria-hidden="true" />;
  }
  if (status === 'current') return <LoaderCircle className="size-4" aria-hidden="true" />;
  return <Circle className="text-muted-foreground size-4" aria-hidden="true" />;
}

function stepStatusLabel(status: StepStatus) {
  switch (status) {
    case 'complete':
      return '已完成';
    case 'needs-attention':
      return '需处理';
    case 'current':
      return '进行中';
    case 'not-started':
      return '未开始';
  }
}

function SaveStatus({
  isDirty,
  isSaving,
  isError,
  hasReferenceSource,
  lastSavedAt,
}: {
  isDirty: boolean;
  isSaving: boolean;
  isError: boolean;
  hasReferenceSource: boolean;
  lastSavedAt: Date | undefined;
}) {
  let content = '没有未保存修改';
  if (isSaving) content = '正在保存';
  else if (isError) content = '保存失败';
  else if (isDirty) content = '有未保存内容';
  else if (hasReferenceSource) content = '参考程序尚未校准';
  else if (lastSavedAt) {
    content = `已保存 ${lastSavedAt.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    })}`;
  }
  return (
    <Text
      className={cn(isError && 'text-danger')}
      size="sm"
      tone="muted"
      role="status"
      aria-live="polite"
    >
      {content}
    </Text>
  );
}

function ProcessSection({
  number,
  title,
  status,
  children,
}: {
  number: number;
  title: string;
  status: string;
  children: ReactNode;
}) {
  return (
    <section className="border-border-soft border-b pb-6 last:border-b-0">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <span className="rounded-circle border-border bg-surface-translucent-selected font-body inline-flex size-8 items-center justify-center border">
          {number}
        </span>
        <Heading level={2} size="lg">
          {title}
        </Heading>
        <Badge>{status}</Badge>
      </div>
      {children}
    </section>
  );
}

function publishCheckStep(code: string): ProblemWorkbenchStep {
  if (code === 'CONTENT') return 'statement';
  if (code === 'SAMPLES') return 'samples';
  if (code === 'LANGUAGE') return 'starter-code';
  if (code === 'TEST_DATA' || code === 'CALIBRATION' || code === 'ONLINE_JUDGE_NODE') {
    return 'test-and-calibrate';
  }
  return 'publish';
}

function ConfirmationDialog({
  title,
  description,
  confirmLabel,
  trigger,
  onConfirm,
  danger = false,
  disabled = false,
  loading = false,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  trigger: ReactElement;
  onConfirm: () => Promise<void>;
  danger?: boolean;
  disabled?: boolean;
  loading?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string>();

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (disabled || loading) return;
        setOpen(nextOpen);
        if (!nextOpen) setError(undefined);
      }}
    >
      <DialogTrigger disabled={disabled} render={trigger} />
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {error ? (
          <Text className="text-danger" role="alert">
            {error}
          </Text>
        ) : null}
        <DialogFooter>
          <DialogClose render={<Button variant="secondary" disabled={loading} />}>取消</DialogClose>
          <Button
            variant={danger ? 'danger' : 'primary'}
            loading={loading}
            onClick={() => {
              void (async () => {
                setError(undefined);
                try {
                  await onConfirm();
                  setOpen(false);
                } catch (caught) {
                  setError(apiErrorMessage(caught, '操作失败，请刷新状态后重试。'));
                }
              })();
            }}
          >
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
