import { useForm, useStore } from '@tanstack/react-form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useBlocker, useNavigate } from '@tanstack/react-router';
import {
  Archive,
  Check,
  CircleDashed,
  CircleDot,
  Copy,
  Cpu,
  FileArchive,
  Gauge,
  Globe,
  Lock,
  MoreHorizontal,
  RotateCcw,
  Save,
  Server,
  Trash2,
} from 'lucide-react';
import { type ReactNode, useEffect, useId, useRef, useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import { CodeBlockEditor } from '@/components/ui/code-block-editor';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { HeaderActions, usePageCrumb } from '@/components/ui/header-slot';
import { IconButton } from '@/components/ui/icon-button';
import { Container, Section } from '@/components/ui/layout';
import { RichMarkdownEditor } from '@/components/ui/rich-markdown-editor';
import { Text } from '@/components/ui/typography';
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
import { codeModeLabels } from '../lib/code-mode';
import { DifficultyIcon, difficultyLabel } from './difficulty-icon';
import { type EditableProblemSample, ProblemSampleList } from './problem-sample-list';
import { ProblemTagProperty } from './problem-tag-property';
import {
  apiErrorMessage,
  CalibrationDialog,
  type CalibrationLimits,
  ConfirmationDialog,
  formatBytes,
  PublishDialog,
  TestDataDialog,
} from './problem-workbench-dialogs';
import { PropertyButton } from './property-button';

/** 工作台上可以直接链接打开的对话框。 */
export type ProblemWorkbenchPanel = 'test-data' | 'calibration' | 'publish';

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

type MarkdownFieldName =
  'inputDescriptionMarkdown' | 'outputDescriptionMarkdown' | 'constraintsMarkdown' | 'hintMarkdown';

const difficulties: ReadonlyArray<ProblemDifficulty> = ['UNRATED', 'EASY', 'MEDIUM', 'HARD'];

// 停止输入多久后自动保存。linear.app 是边写边存；这里留一点间隔，避免每个字都发一次请求。
const AUTOSAVE_DELAY_MS = 800;

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
    judgeTemplate: version.allowedLanguages[0].judgeTemplate ?? '',
  };
}

// 驱动文件按固定文件名包含用户文件（contracts/judge.schema.json 的 x-language-files），用户文件开头已经有 C++ 前缀。
const CPP_DRIVER_SKELETON = `#include "solution.cpp"

int main() {
    // 读入数据，调用 Solution 的方法，输出结果
}
`;

function toProblemSamples(samples: EditableProblemSample[]): ProblemSample[] {
  return samples.map((sample, index) => ({
    ordinal: index + 1,
    input: sample.input,
    output: sample.output,
    explanationMarkdown: sample.explanationMarkdown.trim() || null,
  }));
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
  panel,
  onPanelChange,
}: {
  problemId: string;
  panel: ProblemWorkbenchPanel | undefined;
  onPanelChange: (panel: ProblemWorkbenchPanel | undefined) => void;
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

  return <WorkbenchEditor problem={problem.data} panel={panel} onPanelChange={onPanelChange} />;
}

// 构图取自 linear.app 的 Issue 详情（docs/design-system/measurements.md「详情页」）：
// 左边一页到底地写——标题、题面、输入输出、数据范围、提示、样例、起始代码——没有步骤、没有表单标签和
// 边框；右边一栏属性，点开即在原地改。不属于"写"的动作（测试数据、校准、公开）是属性栏打开的对话框。
//
// 保存：私有题目像 linear.app 一样停止输入后自动保存；已公开题目的修改会立即对学生生效，
// 所以仍由管理员点"保存"（或 ⌘S）确认，避免写到一半的题面被学生看到。
function WorkbenchEditor({
  problem,
  panel,
  onPanelChange,
}: {
  problem: AdminProblem;
  panel: ProblemWorkbenchPanel | undefined;
  onPanelChange: (panel: ProblemWorkbenchPanel | undefined) => void;
}) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const ids = useId();
  const statementId = `${ids}-statement`;
  const uploadController = useRef<AbortController | undefined>(undefined);
  const [lastSavedAt, setLastSavedAt] = useState<Date>();
  const [referenceSource, setReferenceSource] = useState('');
  const [uploadState, setUploadState] = useState<string>();
  const [limits, setLimits] = useState<CalibrationLimits>({
    cpuSeconds: '1',
    memoryMib: '256',
    clockSeconds: '',
  });
  const [confirming, setConfirming] = useState<'archive' | 'delete'>();
  const isCore = problem.codeMode === 'CORE';
  // 改了驱动代码不会让标定失效（只提示）：本次会话里保存过改动后的驱动代码，就提示重新标定，标定成功后收起。
  const savedDriver = useRef(problem.allowedLanguages[0].judgeTemplate ?? '');
  const solutionPrefix = problem.allowedLanguages[0].solutionPrefix;
  // 前缀以换行结尾，行数 = 换行个数（与服务端换算报错行号的规则一致）。
  const prefixLines = solutionPrefix ? solutionPrefix.split('\n').length - 1 : 0;
  const [driverChanged, setDriverChanged] = useState(false);
  // 题目没有草稿：除归档外都可直接编辑，公开题目的修改立即对学生生效。
  const editable = problem.status === 'ACTIVE';
  const isPublic = problem.visibility === 'PUBLIC';
  const autosave = editable && !isPublic;

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

  // 表单只用打开页面时的内容初始化。之后服务端返回的题目（保存结果、刷新）不再写回编辑区：
  // 服务端会规范化部分字段（例如去掉首尾空白），写回会把正在输入的内容和光标打回几百毫秒前。
  const [initialValues] = useState(() => toFormValues(problem));
  // 最近一次成功保存的内容；"有没有未保存修改"就是当前内容是否与它不同。
  const [savedValues, setSavedValues] = useState(initialValues);
  const form = useForm({
    defaultValues: initialValues,
    onSubmit: async ({ value }) => {
      // 标题为空时不保存；提示就在标题下方，不打断输入。
      if (!value.title.trim()) return;
      await save.mutateAsync({ values: cloneWorkbenchValues(value) });
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
        // 驱动代码省略表示不改；ACM 题目不能带它。
        ...(isCore && value.judgeTemplate.trim() ? { judgeTemplate: value.judgeTemplate } : {}),
        rowVersion: problem.rowVersion,
      }),
    onSuccess: async (saved, submitted) => {
      queryClient.setQueryData(problemKeys.adminProblem(problem.id), saved);
      const driver = submitted.values.judgeTemplate;
      if (isCore && driver.trim() && driver !== savedDriver.current) setDriverChanged(true);
      savedDriver.current = driver;
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
  usePageCrumb(formValues.title || problem.title);

  const markChanged = () => {
    if (!save.isPending) save.reset();
  };

  // 自动保存：停止修改一会儿后提交；保存进行中再改，等这次完成后接着存最新内容。
  // 保存失败时停下，等管理员处理（再次修改会清掉错误并恢复自动保存）。
  useEffect(() => {
    if (!autosave || !isDirty || titleMissing || save.isPending || save.isError) return undefined;
    const timer = window.setTimeout(() => void form.handleSubmit(), AUTOSAVE_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [autosave, form, formValues, isDirty, save.isError, save.isPending, titleMissing]);

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
      if (result.status === 'VALID') setDriverChanged(false);
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
  const calibrationReady = Boolean(
    check.data?.checks.some((item) => item.code === 'CALIBRATION' && item.passed),
  );
  const passedChecks = check.data?.checks.filter((item) => item.passed).length ?? 0;
  const totalChecks = check.data?.checks.length ?? 0;

  // 测试数据、校准和公开都以服务端已保存的内容为准，本地还有没存的修改时先等它存好。
  let unsavedReason: string | undefined;
  if (!editable) unsavedReason = '题目已归档，不能再修改。';
  else if (isDirty) {
    unsavedReason = autosave ? '正在保存修改，完成后即可继续。' : '先保存题目修改。';
  }
  let calibrationBlocked = unsavedReason;
  if (calibrationBlocked === undefined && !problem.testData)
    calibrationBlocked = '先上传测试数据。';
  if (calibrationBlocked === undefined && nodeUnavailable) {
    calibrationBlocked = nodeUnavailable.message;
  }

  const conflict =
    save.error instanceof ApiError &&
    (save.error.status === 409 || save.error.code?.includes('CONFLICT'));

  const reloadLatest = async () => {
    const latest = await getAdminProblem(problem.id);
    queryClient.setQueryData(problemKeys.adminProblem(problem.id), latest);
    const latestValues = toFormValues(latest);
    form.reset(latestValues);
    setSavedValues(latestValues);
    save.reset();
  };

  const copyLocalDraft = async () => {
    await navigator.clipboard.writeText(JSON.stringify(formValues, null, 2));
  };

  const scrollToSection = (sectionId: string) => {
    onPanelChange(undefined);
    // 等对话框关闭、焦点归还之后再滚动，避免被焦点恢复拉回去。
    window.setTimeout(() => {
      document.getElementById(sectionId)?.scrollIntoView({ block: 'start', behavior: 'smooth' });
    }, 0);
  };

  const resolveCheck = (code: string) => {
    if (code === 'TEST_DATA') onPanelChange('test-data');
    else if (code === 'CALIBRATION' || code === 'ONLINE_JUDGE_NODE') onPanelChange('calibration');
    else if (code === 'SAMPLES') scrollToSection(`${ids}-samples`);
    else if (code === 'LANGUAGE') scrollToSection(`${ids}-starter-code`);
    else scrollToSection(`${ids}-content`);
  };

  const markdownField = (name: MarkdownFieldName, label: string, placeholder: string) => (
    <form.Field name={name}>
      {(field) => (
        <EditorSection title={label}>
          <RichMarkdownEditor
            aria-label={label}
            placeholder={placeholder}
            value={field.state.value}
            disabled={!editable}
            onChange={(value) => {
              field.handleChange(value);
              markChanged();
            }}
          />
        </EditorSection>
      )}
    </form.Field>
  );

  return (
    <div className="flex min-h-full min-w-0 flex-col">
      <HeaderActions>
        <SaveStatus
          autosave={autosave}
          isDirty={isDirty}
          isSaving={save.isPending}
          isError={save.isError}
          titleMissing={titleMissing}
          lastSavedAt={lastSavedAt}
        />
        {autosave || !editable ? null : (
          <Button
            size="sm"
            disabled={!isDirty || titleMissing}
            loading={save.isPending}
            onClick={() => void form.handleSubmit()}
          >
            <Save aria-hidden="true" />
            保存
          </Button>
        )}
        <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <IconButton label="更多操作">
                <MoreHorizontal aria-hidden="true" />
              </IconButton>
            }
          />
          <DropdownMenuContent>
            <DropdownMenuItem
              disabled={hasUnsavedContent || !editable || isPublic}
              onClick={() => setConfirming('archive')}
            >
              <Archive aria-hidden="true" />
              归档题目
            </DropdownMenuItem>
            <DropdownMenuItem
              variant="danger"
              disabled={hasUnsavedContent || problem.publishedAt !== null}
              onClick={() => setConfirming('delete')}
            >
              <Trash2 aria-hidden="true" />
              删除题目
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </HeaderActions>

      <div className="min-w-0 flex-1 lg:grid lg:grid-cols-[minmax(0,1fr)_17rem]">
        <form
          className="min-w-0 px-4 pt-10 pb-24 sm:px-8 xl:px-14"
          onSubmit={(event) => {
            event.preventDefault();
            void form.handleSubmit();
          }}
        >
          <div className="mx-auto max-w-[43rem] min-w-0">
            <form.Field name="title">
              {(field) => (
                <textarea
                  aria-label="题目标题"
                  rows={1}
                  value={field.state.value}
                  readOnly={!editable}
                  placeholder="题目标题"
                  onChange={(event) => {
                    // 标题是一行：粘贴进来的换行折成空格。
                    field.handleChange(event.target.value.replace(/\s*\n\s*/g, ' '));
                    markChanged();
                  }}
                  onKeyDown={(event) => {
                    // 与 linear.app 一致：在标题里按 Enter 进入正文。
                    if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
                      event.preventDefault();
                      document.getElementById(statementId)?.focus();
                    }
                  }}
                  className="text-foreground font-heading placeholder:text-fg-disabled block field-sizing-content w-full resize-none bg-transparent text-2xl leading-[38px] tracking-[-0.1px] outline-none"
                />
              )}
            </form.Field>
            {titleMissing && editable ? (
              <Text className="text-danger mt-1" size="sm" role="alert">
                标题不能为空，填写之前修改不会保存。
              </Text>
            ) : null}
            <Text size="sm" tone="meta" className="mt-1">
              {problem.slug}
              {!editable
                ? ' · 这道题已归档，当前为只读状态'
                : isPublic
                  ? ' · 这道题已公开，保存会立即对学生生效'
                  : null}
            </Text>

            {save.isError ? (
              <div className="mt-6 grid gap-2" role="alert">
                <Text weight="medium">
                  {conflict ? '服务端已有更新，本地内容仍保留' : '保存失败'}
                </Text>
                <Text className="text-danger" size="sm">
                  {conflict
                    ? '先复制本地内容，再加载服务端最新版本并手动合并；不会自动覆盖。'
                    : apiErrorMessage(save.error, '请检查内容后重试。')}
                </Text>
                <div className="flex flex-wrap gap-2">
                  {conflict ? (
                    <Button variant="secondary" size="sm" onClick={() => void copyLocalDraft()}>
                      <Copy aria-hidden="true" />
                      复制本地内容
                    </Button>
                  ) : (
                    <Button variant="secondary" size="sm" onClick={() => void form.handleSubmit()}>
                      <RotateCcw aria-hidden="true" />
                      重试保存
                    </Button>
                  )}
                  <Button variant="secondary" size="sm" onClick={() => void reloadLatest()}>
                    加载服务端最新版本
                  </Button>
                </div>
              </div>
            ) : null}

            <div id={`${ids}-content`} className="mt-6 scroll-mt-6">
              <div>
                <form.Field name="statementMarkdown">
                  {(field) => (
                    <RichMarkdownEditor
                      id={statementId}
                      aria-label="题目正文"
                      placeholder="描述题目…（支持 Markdown：## 标题、**粗体**、``` 代码块）"
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
              {markdownField('inputDescriptionMarkdown', '输入说明', '说明输入的格式…')}
              {markdownField('outputDescriptionMarkdown', '输出说明', '说明输出的格式…')}
              {markdownField('constraintsMarkdown', '数据范围', '可选')}
              {markdownField('hintMarkdown', '提示', '可选')}
            </div>

            <EditorSection id={`${ids}-samples`} title="样例">
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
            </EditorSection>

            <EditorSection
              id={`${ids}-starter-code`}
              title="起始代码"
              description="C++，留空时答题者会得到空白编辑器。"
            >
              <form.Field name="starterCode">
                {(field) => (
                  <CodeBlockEditor
                    language="cpp"
                    value={field.state.value}
                    readOnly={!editable}
                    aria-label="C++ 起始代码"
                    onChange={(value) => {
                      field.handleChange(value);
                      markChanged();
                    }}
                  />
                )}
              </form.Field>
            </EditorSection>

            {isCore && solutionPrefix ? (
              <EditorSection
                id={`${ids}-solution-prefix`}
                title="前缀代码"
                description={`C++ 所有核心代码题共用，在"语言"页修改。用户文件 solution.cpp = 这 ${prefixLines} 行 + 用户代码。`}
              >
                <CodeBlockEditor
                  language="cpp"
                  value={solutionPrefix}
                  readOnly
                  aria-label="C++ 前缀代码"
                  onChange={() => undefined}
                />
              </EditorSection>
            ) : null}

            {isCore ? (
              <form.Field name="judgeTemplate">
                {(field) => (
                  <EditorSection
                    id={`${ids}-judge-template`}
                    title="驱动代码"
                    description="引用 solution.cpp（前缀代码 + 用户代码）；报错里会显示它，不要写答案。"
                    actions={
                      editable && !field.state.value.trim() ? (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => {
                            field.handleChange(CPP_DRIVER_SKELETON);
                            markChanged();
                          }}
                        >
                          填入骨架
                        </Button>
                      ) : driverChanged ? (
                        <span className="flex items-center gap-2">
                          <Text as="span" size="sm" className="text-warning" role="status">
                            已修改，建议重新标定
                          </Text>
                          <Button
                            size="sm"
                            variant="secondary"
                            onClick={() => onPanelChange('calibration')}
                          >
                            重新标定
                          </Button>
                        </span>
                      ) : null
                    }
                  >
                    <CodeBlockEditor
                      language="cpp"
                      value={field.state.value}
                      readOnly={!editable}
                      aria-label="C++ 驱动代码"
                      onChange={(value) => {
                        field.handleChange(value);
                        markChanged();
                      }}
                    />
                  </EditorSection>
                )}
              </form.Field>
            ) : null}
          </div>
        </form>

        <aside
          aria-label="题目属性"
          className="border-border-soft lg:border-l-hairline min-w-0 px-4 pb-8 lg:sticky lg:top-0 lg:self-start lg:px-6 lg:pt-10"
        >
          <div className="grid gap-6">
            <PropertyGroup title="属性">
              <form.Field name="difficulty">
                {(field) => (
                  <DropdownMenu>
                    <DropdownMenuTrigger
                      disabled={!editable}
                      render={
                        <PropertyButton
                          aria-label={`难度：${difficultyLabel(field.state.value)}`}
                          icon={<DifficultyIcon difficulty={field.state.value} />}
                          muted={field.state.value === 'UNRATED'}
                        />
                      }
                    >
                      {field.state.value === 'UNRATED'
                        ? '设置难度'
                        : difficultyLabel(field.state.value)}
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="start" className="min-w-48">
                      {difficulties.map((value) => (
                        <DropdownMenuItem
                          key={value}
                          onClick={() => {
                            field.handleChange(value);
                            markChanged();
                          }}
                        >
                          <DifficultyIcon difficulty={value} />
                          <span className="flex-1">{difficultyLabel(value)}</span>
                          {value === field.state.value ? (
                            <Check className="text-fg-2" aria-hidden="true" />
                          ) : null}
                        </DropdownMenuItem>
                      ))}
                    </DropdownMenuContent>
                  </DropdownMenu>
                )}
              </form.Field>
              <PropertyButton
                icon={isPublic ? <Globe aria-hidden="true" /> : <Lock aria-hidden="true" />}
                disabled={!editable}
                onClick={() => onPanelChange('publish')}
              >
                {isPublic ? '已公开' : '私有'}
              </PropertyButton>
              <PropertyButton icon={<Cpu aria-hidden="true" />} disabled>
                {codeModeLabels[problem.codeMode]} · C++
              </PropertyButton>
            </PropertyGroup>

            <PropertyGroup title="标签">
              <form.Field name="tags">
                {(field) => (
                  <ProblemTagProperty
                    value={field.state.value}
                    disabled={!editable}
                    onChange={(value) => {
                      field.handleChange(value);
                      markChanged();
                    }}
                  />
                )}
              </form.Field>
            </PropertyGroup>

            <PropertyGroup title="判题准备">
              <PropertyButton
                icon={<FileArchive aria-hidden="true" />}
                muted={!problem.testData}
                onClick={() => onPanelChange('test-data')}
              >
                {problem.testData
                  ? `测试数据 ${problem.testData.testcaseCount} 组 · ${formatBytes(problem.testData.totalBytes)}`
                  : '上传测试数据'}
              </PropertyButton>
              <PropertyButton
                icon={
                  calibrationReady ? (
                    <Gauge className="text-success" aria-hidden="true" />
                  ) : (
                    <Gauge aria-hidden="true" />
                  )
                }
                muted={!calibrationReady}
                onClick={() => onPanelChange('calibration')}
              >
                {calibrationReady ? '已校准' : '运行校准'}
              </PropertyButton>
              <PropertyButton icon={<Server aria-hidden="true" />} disabled>
                {nodeUnavailable
                  ? '没有在线判题节点'
                  : check.data
                    ? '判题节点在线'
                    : '正在检查判题节点…'}
              </PropertyButton>
            </PropertyGroup>

            <PropertyGroup title="公开">
              <PropertyButton
                icon={
                  check.data?.ready ? (
                    <CircleDot className="text-success" aria-hidden="true" />
                  ) : (
                    <CircleDashed aria-hidden="true" />
                  )
                }
                onClick={() => onPanelChange('publish')}
              >
                {!check.data
                  ? '正在检查…'
                  : isPublic
                    ? '公开设置'
                    : check.data.ready
                      ? '可以公开'
                      : `公开检查 ${passedChecks} / ${totalChecks}`}
              </PropertyButton>
            </PropertyGroup>
          </div>
        </aside>
      </div>

      <TestDataDialog
        open={panel === 'test-data'}
        onOpenChange={(open) => onPanelChange(open ? 'test-data' : undefined)}
        testData={problem.testData}
        blockedReason={unsavedReason}
        uploading={upload.isPending}
        uploadState={uploadState}
        onUpload={(file) => {
          uploadController.current?.abort();
          const controller = new AbortController();
          uploadController.current = controller;
          upload.mutate({ file, signal: controller.signal });
        }}
        onCancelUpload={() => uploadController.current?.abort()}
      />
      <CalibrationDialog
        open={panel === 'calibration'}
        onOpenChange={(open) => onPanelChange(open ? 'calibration' : undefined)}
        limits={limits}
        onLimitsChange={setLimits}
        referenceLabel={isCore ? '参考核心代码' : '参考程序'}
        referenceSource={referenceSource}
        onReferenceSourceChange={setReferenceSource}
        editable={editable}
        blockedReason={calibrationBlocked}
        running={calibration.isPending}
        resultMessage={
          calibration.data
            ? `校准状态：${calibration.data.status}。参考源码已从页面内存清除。`
            : undefined
        }
        errorMessage={
          calibration.isError
            ? apiErrorMessage(calibration.error, '校准失败，参考程序仍保留在当前页面。')
            : undefined
        }
        onRun={() => calibration.mutate()}
      />
      <PublishDialog
        open={panel === 'publish'}
        onOpenChange={(open) => onPanelChange(open ? 'publish' : undefined)}
        check={check.data}
        checkPending={check.isPending}
        checkFetching={check.isFetching}
        checkError={
          check.isError ? apiErrorMessage(check.error, '公开检查失败，请刷新题目状态。') : undefined
        }
        onRecheck={() => void check.refetch()}
        isPublic={isPublic}
        blockedReason={unsavedReason}
        acting={publishing.isPending || unpublishing.isPending}
        actionError={
          publishing.isError || unpublishing.isError
            ? apiErrorMessage(
                publishing.error ?? unpublishing.error,
                '操作失败，题目状态保持不变。',
              )
            : undefined
        }
        onPublish={() =>
          publishing.mutate(undefined, { onSuccess: () => onPanelChange(undefined) })
        }
        onUnpublish={() =>
          unpublishing.mutate(undefined, { onSuccess: () => onPanelChange(undefined) })
        }
        onResolve={resolveCheck}
      />
      <ConfirmationDialog
        open={confirming === 'archive'}
        onOpenChange={(open) => setConfirming(open ? 'archive' : undefined)}
        title={`归档题目 ${problem.slug}`}
        description="归档会让整道题退出正常管理流程，之后不能再修改。已公开的题目要先取消公开。"
        confirmLabel="归档题目"
        loading={archive.isPending}
        onConfirm={() => archive.mutateAsync().then(() => undefined)}
      />
      <ConfirmationDialog
        open={confirming === 'delete'}
        onOpenChange={(open) => setConfirming(open ? 'delete' : undefined)}
        title={`删除题目 ${problem.slug}`}
        description="这个动作不可恢复，题面、样例和测试数据一并删除。只有从未公开过的题目允许删除，公开过的只能归档。"
        confirmLabel="永久删除题目"
        loading={remove.isPending}
        onConfirm={() => remove.mutateAsync().then(() => undefined)}
      />

      <Dialog open={blocker.status === 'blocked'}>
        <DialogContent size="sm">
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
    </div>
  );
}

// 区块标题：linear.app 详情页的区块标题 15px/600（"Sub-issues"、"Activity"）。
function EditorSection({
  id,
  title,
  description,
  actions,
  children,
}: {
  id?: string;
  title: string;
  description?: string;
  /** 标题行末尾的操作，只放与这一节内容直接相关的按钮。 */
  actions?: ReactNode;
  children: ReactNode;
}) {
  const headingId = useId();
  return (
    <section id={id} aria-labelledby={headingId} className="mt-8 scroll-mt-6">
      <div className="mb-3 flex flex-wrap items-baseline gap-x-2">
        <h2 id={headingId} className="text-foreground font-heading text-15">
          {title}
        </h2>
        {description ? (
          <Text as="span" size="sm" tone="meta">
            {description}
          </Text>
        ) : null}
        {actions ? <span className="ml-auto">{actions}</span> : null}
      </div>
      {children}
    </section>
  );
}

// 属性栏的组：组标题 13px/500 灰，下面是一列属性胶囊（linear.app 详情页右栏）。
function PropertyGroup({ title, children }: { title: string; children: ReactNode }) {
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className="grid justify-items-start gap-0.5">
      <h2 id={headingId} className="text-fg-meta font-body mb-1 px-1.5 text-sm">
        {title}
      </h2>
      {children}
    </section>
  );
}

function SaveStatus({
  autosave,
  isDirty,
  isSaving,
  isError,
  titleMissing,
  lastSavedAt,
}: {
  autosave: boolean;
  isDirty: boolean;
  isSaving: boolean;
  isError: boolean;
  titleMissing: boolean;
  lastSavedAt: Date | undefined;
}) {
  let content = '';
  if (isSaving) content = '正在保存…';
  else if (isError) content = '保存失败';
  else if (isDirty && titleMissing) content = '标题为空，暂未保存';
  else if (isDirty) content = autosave ? '正在保存…' : '有未保存修改';
  else if (lastSavedAt) {
    content = `已保存 ${lastSavedAt.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    })}`;
  } else if (autosave) content = '修改会自动保存';
  return (
    <Text
      className={cn('max-sm:sr-only', isError && 'text-danger')}
      size="sm"
      tone="meta"
      role="status"
      aria-live="polite"
    >
      {content}
    </Text>
  );
}
