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
  FlaskConical,
  Gauge,
  Globe,
  Lock,
  MoreHorizontal,
  RotateCcw,
  Save,
  Server,
  Trash2,
} from 'lucide-react';
import { type ReactNode, useId, useRef, useState } from 'react';

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
import { apiErrorMessage } from '@/lib/api/api-error-message';
import { cn } from '@/lib/utils';

import type { AuthoringProposal } from '../api/authoring-api';
import { archiveProblem, deleteProblem, getAdminProblem, problemKeys } from '../api/problems-api';
import { useProblemCalibration } from '../hooks/use-problem-calibration';
import { useProblemPublication } from '../hooks/use-problem-publication';
import { useTestDataUpload } from '../hooks/use-test-data-upload';
import { codeModeLabels } from '../lib/code-mode';
import { DifficultyIcon, difficultyLabel } from './difficulty-icon';
import { ProblemAuthoringDialog } from './problem-authoring-dialog';
import { ProblemLanguagesEditor } from './problem-languages-editor';
import { ProblemSampleList } from './problem-sample-list';
import { ProblemTagProperty } from './problem-tag-property';
import {
  CalibrationDialog,
  ConfirmationDialog,
  formatBytes,
  PublishDialog,
  TestDataDialog,
} from './problem-workbench-dialogs';
import { PropertyButton } from './property-button';
import { useProblemWorkbenchSave } from './use-problem-workbench-save';

/** 工作台上可以直接链接打开的对话框。 */
export type ProblemWorkbenchPanel = 'test-data' | 'calibration' | 'publish' | 'authoring';

type MarkdownFieldName =
  'inputDescriptionMarkdown' | 'outputDescriptionMarkdown' | 'constraintsMarkdown' | 'hintMarkdown';

const difficulties: ReadonlyArray<ProblemDifficulty> = ['UNRATED', 'EASY', 'MEDIUM', 'HARD'];

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
  const titleId = `${ids}-title`;
  const statementId = `${ids}-statement`;
  const samplesId = `${ids}-samples`;
  const languagesId = `${ids}-languages`;
  const publicationFocus = useRef<HTMLElement | null>(null);
  const [confirming, setConfirming] = useState<'archive' | 'delete'>();
  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: problemKeys.adminProblem(problem.id) }),
      queryClient.invalidateQueries({ queryKey: problemKeys.admin }),
      queryClient.invalidateQueries({ queryKey: problemKeys.publishCheck(problem.id) }),
    ]);
  };

  const publication = useProblemPublication(problem.id, problem.rowVersion, refresh);
  const { check, nodeUnavailable, calibrationReady, passedChecks, totalChecks } = publication;
  const calibration = useProblemCalibration(problem, check, refresh);
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
  } = useProblemWorkbenchSave(problem, calibration.hasReferenceSource);
  usePageCrumb(title || problem.title);

  const upload = useTestDataUpload(problem.id, refresh);

  const adoptProposal = (proposal: AuthoringProposal) => {
    form.setFieldValue('title', proposal.title);
    form.setFieldValue('statementMarkdown', proposal.statementMarkdown);
    form.setFieldValue('inputDescriptionMarkdown', proposal.inputDescriptionMarkdown);
    form.setFieldValue('outputDescriptionMarkdown', proposal.outputDescriptionMarkdown);
    form.setFieldValue('constraintsMarkdown', proposal.constraintsMarkdown);
    form.setFieldValue(
      'samples',
      proposal.samples.map((sample) => ({
        input: sample.input,
        output: sample.output,
        explanationMarkdown: sample.explanationMarkdown ?? '',
      })),
    );
    languageEditor.current?.stage(proposal.languages);
    markChanged();
  };

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

  const goToSection = (sectionId: string) => {
    publicationFocus.current = document.getElementById(sectionId);
    onPanelChange(undefined);
  };

  const resolveCheck = (code: string) => {
    if (code === 'TEST_DATA') onPanelChange('test-data');
    else if (code === 'CALIBRATION') onPanelChange('calibration');
    else if (code === 'ONLINE_JUDGE_NODE') void navigate({ to: '/admin' });
    else if (code === 'SAMPLES') goToSection(samplesId);
    else if (code === 'LANGUAGE') goToSection(languagesId);
    else if (code === 'CONTENT') goToSection(titleId);
  };

  const readyToPublish = check?.ready && !publication.checkError && !unsavedReason;
  const publishControl = (
    <PropertyButton
      aria-haspopup="dialog"
      icon={
        readyToPublish ? (
          <CircleDot className="text-success" aria-hidden="true" />
        ) : (
          <CircleDashed aria-hidden="true" />
        )
      }
      onClick={() => onPanelChange('publish')}
    >
      {publication.checkError
        ? '公开检查失败'
        : isPublic
          ? '公开设置'
          : !check
            ? '正在运行公开检查…'
            : readyToPublish
              ? '可以公开'
              : check.ready && editable
                ? '公开前先保存'
                : `公开检查 ${passedChecks} / ${totalChecks}`}
    </PropertyButton>
  );

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
                  id={titleId}
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
                  className="text-foreground font-heading placeholder:text-fg-disabled block field-sizing-content w-full resize-none scroll-mt-[calc(var(--layout-header)+var(--space-6))] bg-transparent text-2xl leading-[38px] tracking-[-0.1px] outline-none md:scroll-mt-6"
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
            <div className="mt-3 lg:hidden">{publishControl}</div>

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

            <div className="mt-6">
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

            <EditorSection id={samplesId} title="样例">
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
              sectionId={languagesId}
              problem={problem}
              blocked={blockLanguageSave}
              onDirtyChange={setLanguagesDirty}
              saveRef={languageEditor}
              onSavingChange={setLanguageSaving}
              onCalibrate={(id) => {
                calibration.setLanguageId(id);
                onPanelChange('calibration');
              }}
              calibration={calibration.result}
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
                icon={<FlaskConical aria-hidden="true" />}
                onClick={() => onPanelChange('authoring')}
              >
                自动出题
              </PropertyButton>
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
              <Text size="sm" tone="meta" className="flex items-start gap-2 px-1.5 py-1">
                <Server className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
                <span>
                  {!problem.testData
                    ? '上传测试数据后检查判题节点'
                    : publication.checkError
                      ? '判题节点状态未确认'
                      : nodeUnavailable
                        ? nodeUnavailable.message
                        : check
                          ? '判题节点在线'
                          : '正在检查判题节点…'}
                </span>
              </Text>
            </PropertyGroup>

            <div className="hidden lg:block">
              <PropertyGroup title="公开">{publishControl}</PropertyGroup>
            </div>
          </div>
        </aside>
      </div>

      <TestDataDialog
        open={panel === 'test-data'}
        onOpenChange={(open) => onPanelChange(open ? 'test-data' : undefined)}
        testData={problem.testData}
        blockedReason={unsavedReason}
        isUploading={upload.isUploading}
        uploadMessage={upload.uploadMessage}
        onUpload={upload.startUpload}
        onCancelUpload={upload.cancelUpload}
      />
      <ProblemAuthoringDialog
        open={panel === 'authoring'}
        onOpenChange={(open) => onPanelChange(open ? 'authoring' : undefined)}
        problem={problem}
        check={check}
        blockedReason={unsavedReason}
        refresh={refresh}
        onAdopt={adoptProposal}
        onCalibrate={() => onPanelChange('calibration')}
      />
      <CalibrationDialog
        open={panel === 'calibration'}
        onOpenChange={(open) => onPanelChange(open ? 'calibration' : undefined)}
        configuration={
          <div className="flex flex-wrap gap-3">
            <SelectField
              label="标定语言"
              value={calibration.language.id}
              disabled={calibration.isRunning}
              items={problem.allowedLanguages.map((item) => ({
                value: item.id,
                label: item.displayName,
              }))}
              onValueChange={calibration.selectLanguage}
            />
            <SelectField
              label="标定方式"
              value={calibration.kind}
              disabled={calibration.isRunning}
              items={[
                { value: 'BASE', label: '基础标定' },
                { value: 'OVERRIDE', label: '覆盖标定' },
              ]}
              onValueChange={(kind) => {
                if (kind === 'BASE' || kind === 'OVERRIDE') calibration.setKind(kind);
              }}
            />
          </div>
        }
        limits={calibration.limits}
        onLimitsChange={calibration.setLimits}
        referenceLabel={calibration.referenceLabel}
        languageId={calibration.language.id}
        languageName={calibration.language.displayName}
        referenceSource={calibration.referenceSource}
        onReferenceSourceChange={calibration.setReferenceSource}
        editable={editable}
        blockedReason={calibration.blockedReason(unsavedReason)}
        running={calibration.isRunning}
        resultMessage={calibration.resultMessage}
        errorMessage={calibration.errorMessage}
        onRun={calibration.run}
      />
      <PublishDialog
        open={panel === 'publish'}
        onOpenChange={(open) => onPanelChange(open ? 'publish' : undefined)}
        onCloseComplete={() => {
          const target = publicationFocus.current;
          publicationFocus.current = null;
          if (target === null) return;
          // 先让对话框完成焦点归还，再把键盘焦点和视口移到编辑区。
          window.requestAnimationFrame(() => {
            target.focus({ preventScroll: true });
            target.scrollIntoView({ block: 'start' });
          });
        }}
        check={check}
        languages={problem.allowedLanguages}
        checkPending={publication.isChecking}
        checkFetching={publication.isRefreshingCheck}
        checkError={publication.checkError}
        onRecheck={publication.refreshCheck}
        isPublic={isPublic}
        blockedReason={unsavedReason}
        acting={publication.isChangingVisibility}
        actionError={publication.actionError}
        onPublish={() => publication.publish(() => onPanelChange(undefined))}
        onUnpublish={() => publication.unpublish(() => onPanelChange(undefined))}
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
              {calibration.hasReferenceSource ? '参考程序只在当前页面内存中，离开后会丢失。' : ''}
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
    <section
      id={id}
      tabIndex={id === undefined ? undefined : -1}
      aria-labelledby={headingId}
      className="focus-visible:outline-ring mt-8 scroll-mt-[calc(var(--layout-header)+var(--space-6))] focus-visible:outline-1 md:scroll-mt-6"
    >
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
