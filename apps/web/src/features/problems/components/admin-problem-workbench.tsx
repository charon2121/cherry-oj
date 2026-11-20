import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
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
import { SelectField } from '@/components/ui/select';
import { Text } from '@/components/ui/typography';
import type { AdminProblem, ProblemDifficulty } from '@/generated/api';
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
} from '../api/problems-api';
import { codeModeLabels } from '../lib/code-mode';
import { DifficultyIcon, difficultyLabel } from './difficulty-icon';
import { ProblemLanguagesEditor } from './problem-languages-editor';
import { ProblemSampleList } from './problem-sample-list';
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
import { useProblemWorkbenchSave } from './use-problem-workbench-save';

/** 工作台上可以直接链接打开的对话框。 */
export type ProblemWorkbenchPanel = 'test-data' | 'calibration' | 'publish';

type MarkdownFieldName =
  'inputDescriptionMarkdown' | 'outputDescriptionMarkdown' | 'constraintsMarkdown' | 'hintMarkdown';

const difficulties: ReadonlyArray<ProblemDifficulty> = ['UNRATED', 'EASY', 'MEDIUM', 'HARD'];

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
// 边框；右边一栏属性，点开即在原地改。不属于"写"的动作（测试数据、标定、公开）是属性栏打开的对话框。
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
  const [references, setReferences] = useState<Record<string, string>>({});
  const [calibrationLanguageId, setCalibrationLanguageId] = useState('');
  const [calibrationKind, setCalibrationKind] = useState<'BASE' | 'OVERRIDE'>('BASE');
  const [uploadState, setUploadState] = useState<string>();
  const [limits, setLimits] = useState<CalibrationLimits>({
    cpuSeconds: '1',
    memoryMib: '256',
    clockSeconds: '',
  });
  const [confirming, setConfirming] = useState<'archive' | 'delete'>();
  const isCore = problem.codeMode === 'CORE';
  const language =
    problem.allowedLanguages.find((item) => item.id === calibrationLanguageId) ??
    problem.allowedLanguages[0];
  if (!language) throw new Error('题目语言配置为空。');
  const referenceSource = references[language.id] ?? '';
  const setReferenceSource = (value: string) =>
    setReferences((previous) => ({ ...previous, [language.id]: value }));
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

  const hasReferenceSource = Object.values(references).some((value) => value.trim().length > 0);
  const {
    form,
    title,
    titleMissing,
    editable,
    isPublic,
    autosave,
    hasUnsavedEdits,
    hasUnsavedContent,
    unsavedReason,
    isSaving,
    saveError,
    conflict,
    lastSavedAt,
    languageEditor,
    blockLanguageSave,
    setLanguagesDirty,
    setLanguageSaving,
    blocker,
    markChanged,
    reloadLatest,
    copyLocalDraft,
  } = useProblemWorkbenchSave(problem, hasReferenceSource);
  usePageCrumb(title || problem.title);

  const upload = useMutation({
    mutationFn: ({ file, signal }: { file: File; signal: AbortSignal }) =>
      replaceTestData(problem.id, file, signal),
    onMutate: () => setUploadState('正在上传并检查 ZIP…'),
    onSuccess: async (uploaded) => {
      uploadController.current = undefined;
      setUploadState(
        `已替换测试数据，共 ${uploaded.testcaseCount} 组。旧的标定已过期，请重新标定。`,
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
        languageId: language.id,
        kind: calibrationKind,
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
      setReferences((previous) => ({ ...previous, [result.languageId]: '' }));
      await refresh();
      await queryClient.invalidateQueries({ queryKey: problemKeys.limits(problem.id) });
      await queryClient.invalidateQueries({ queryKey: problemKeys.readyLanguages(problem.slug) });
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

  let calibrationBlocked = unsavedReason;
  if (calibrationBlocked === undefined && !problem.testData)
    calibrationBlocked = '先上传测试数据。';
  const selectedChecks =
    check.data?.languages?.find((item) => item.languageId === language.id)?.checks ??
    check.data?.checks;
  const unavailable = selectedChecks?.find(
    (item) => (item.code === 'ONLINE_JUDGE_NODE' || item.code === 'LANGUAGE') && !item.passed,
  );
  if (calibrationBlocked === undefined && unavailable) calibrationBlocked = unavailable.message;
  if (calibrationBlocked === undefined && isCore && !language.judgeTemplate?.trim())
    calibrationBlocked = '先保存这门语言的驱动代码。';

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
          isDirty={hasUnsavedEdits}
          isSaving={isSaving}
          isError={saveError !== null}
          titleMissing={titleMissing}
          lastSavedAt={lastSavedAt}
        />
        {autosave || !editable ? null : (
          <Button
            size="sm"
            disabled={!hasUnsavedEdits || titleMissing || isSaving}
            loading={isSaving}
            onClick={() => void form.handleSubmit()}
          >
            <Save aria-hidden="true" />
            {/* 窄屏顶栏放不下文字（导航、品牌、保存、更多、主题、账号），只留图标；名字仍给读屏。 */}
            <span className="max-sm:sr-only">保存</span>
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

            {saveError !== null ? (
              <div className="mt-6 grid gap-2" role="alert">
                <Text weight="medium">
                  {conflict ? '服务端已有更新，本地内容仍保留' : '保存失败'}
                </Text>
                <Text className="text-danger" size="sm">
                  {conflict
                    ? '先复制本地内容，再加载服务端最新版本并手动合并；不会自动覆盖。'
                    : apiErrorMessage(saveError, '请检查内容后重试。')}
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

            <ProblemLanguagesEditor
              problem={problem}
              blocked={blockLanguageSave}
              onDirtyChange={setLanguagesDirty}
              saveRef={languageEditor}
              onSavingChange={setLanguageSaving}
              onCalibrate={(id) => {
                setCalibrationLanguageId(id);
                onPanelChange('calibration');
              }}
              calibration={calibration.data}
            />
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
                {codeModeLabels[problem.codeMode]} ·{' '}
                {problem.allowedLanguages.map((item) => item.displayName).join(' / ')}
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
                {calibrationReady ? '已标定' : '运行标定'}
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
        configuration={
          <div className="flex flex-wrap gap-3">
            <SelectField
              label="标定语言"
              value={language.id}
              disabled={calibration.isPending}
              items={problem.allowedLanguages.map((item) => ({
                value: item.id,
                label: item.displayName,
              }))}
              onValueChange={(id) => {
                setCalibrationLanguageId(id);
                calibration.reset();
              }}
            />
            <SelectField
              label="标定方式"
              value={calibrationKind}
              disabled={calibration.isPending}
              items={[
                { value: 'BASE', label: '基础标定' },
                { value: 'OVERRIDE', label: '覆盖标定' },
              ]}
              onValueChange={(kind) => {
                if (kind === 'BASE' || kind === 'OVERRIDE') setCalibrationKind(kind);
              }}
            />
          </div>
        }
        limits={limits}
        onLimitsChange={setLimits}
        referenceLabel={isCore ? '参考核心代码' : '参考程序'}
        languageId={language.id}
        languageName={language.displayName}
        referenceSource={referenceSource}
        onReferenceSourceChange={setReferenceSource}
        editable={editable}
        blockedReason={calibrationBlocked}
        running={calibration.isPending}
        resultMessage={
          calibration.data
            ? `标定状态：${calibration.data.status}。参考源码已从页面内存清除。`
            : undefined
        }
        errorMessage={
          calibration.isError
            ? apiErrorMessage(calibration.error, '标定失败，参考程序仍保留在当前页面。')
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
              {hasUnsavedEdits ? '题目修改尚未保存。' : ''}
              {hasReferenceSource ? '参考程序只在当前页面内存中，离开后会丢失。' : ''}
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
