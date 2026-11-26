import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { SelectField } from '@/components/ui/select';
import { TextEditor } from '@/components/ui/text-editor';
import { Text } from '@/components/ui/typography';
import type { AdminProblem, BuildPlan, ProblemDifficulty, PublishCheck } from '@/generated/api';
import { apiErrorMessage } from '@/lib/api/api-error-message';

import {
  applyAuthoringBuild,
  type AuthoringJob,
  type AuthoringJobDetail,
  authoringPlanSchema,
  type AuthoringProposal,
  authoringReportPath,
  changeAuthoringJob,
  type CreateAuthoringJob,
  createAuthoringJob,
  getAuthoringJob,
  listAuthoringJobs,
} from '../api/authoring-api';
import { problemKeys } from '../api/problems-api';
import { difficultyLabel } from './difficulty-icon';
import { ConfirmationDialog, formatBytes } from './problem-workbench-dialogs';
import { SafeMarkdown } from './safe-markdown';

const statuses: Record<AuthoringJob['status'], string> = {
  QUEUED: '待执行',
  RUNNING: '运行中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
};
const stages: Record<AuthoringJob['stage'], string> = {
  QUEUED: '等待执行',
  MODEL: '生成提案',
  INPUT: '读取构建输入',
  GENERATOR: '生成输入',
  VALIDATOR: '校验输入',
  ANSWERS: '生成答案',
  ORACLE: '核对答案',
  LANGUAGES: '验题',
  EXPORT: '导出产物',
  COMPLETE: '完成',
};
const errorMessages: Record<NonNullable<AuthoringJobDetail['errorCode']>, string> = {
  AUTHORING_RUNNER_UNAVAILABLE: '验题执行器或产物暂不可用。',
  AUTHORING_BUILD_FAILED: '验题失败，请检查报告。',
  AUTHORING_MODEL_UNAVAILABLE: '模型服务暂不可用。',
  AUTHORING_MODEL_INVALID_RESULT: '模型返回的候选内容不符合构建输入格式，可以修订后重试。',
  AUTHORING_TIMEOUT: '任务超时。',
  AUTHORING_INTERRUPTED: '任务中断，可以重试。',
};
const difficulties: ProblemDifficulty[] = ['UNRATED', 'EASY', 'MEDIUM', 'HARD'];

function isActive(status: AuthoringJob['status']) {
  return status === 'QUEUED' || status === 'RUNNING';
}

function buildTemplate(problem: AdminProblem): string {
  const cases = problem.samples.map((sample) => ({
    name: `sample-${sample.ordinal}`,
    generatorInput: '',
    oracle: true,
    sampleOrdinal: sample.ordinal,
  }));
  const plan: BuildPlan = {
    generatorSource: '',
    validatorSource: '',
    oracleSource: '',
    answerLanguageId: problem.allowedLanguages[0]?.id ?? 'cpp',
    cases,
    languages: problem.allowedLanguages.slice(0, 4).map((language) => ({
      languageId: language.id,
      limits: { cpuNs: 1_000_000_000, clockNs: 3_000_000_000, memoryBytes: 268_435_456 },
      referenceSource: '',
      wrongSolutions: [{ source: '', witnessCaseName: cases[0]?.name ?? '' }],
    })),
  };
  return JSON.stringify(plan, null, 2);
}

export function ProblemAuthoringDialog({
  open,
  onOpenChange,
  problem,
  blockedReason,
  check,
  refresh,
  onAdopt,
  onCalibrate,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  problem: AdminProblem;
  blockedReason: string | undefined;
  check: PublishCheck | undefined;
  refresh: () => Promise<void>;
  onAdopt: (proposal: AuthoringProposal) => void;
  onCalibrate: () => void;
}) {
  const client = useQueryClient();
  const knowledgeId = useId();
  const [view, setView] = useState<'build' | 'proposal' | 'jobs'>('jobs');
  const [editedPlan, setPlan] = useState<string>();
  const plan = editedPlan ?? buildTemplate(problem);
  const [selected, setSelected] = useState<string>();
  const [formError, setFormError] = useState('');
  const [knowledgePoint, setKnowledgePoint] = useState('');
  const [difficulty, setDifficulty] = useState<ProblemDifficulty>(problem.difficulty);
  const [goal, setGoal] = useState('');
  const [revision, setRevision] = useState<{ id: string; instructions: string }>();
  const [confirmed, setConfirmed] = useState<'apply' | 'adopt'>();
  const key = [...problemKeys.adminProblem(problem.id), 'authoring'] as const;
  const jobs = useQuery({
    queryKey: [...key, 'list'],
    queryFn: ({ signal }) => listAuthoringJobs(problem.id, signal),
    enabled: open,
    refetchInterval: ({ state }) =>
      state.data?.some((job) => isActive(job.status)) ? 1_500 : false,
    retry: false,
  });
  const id = selected ?? jobs.data?.[0]?.id;
  const detail = useQuery({
    queryKey: [...key, id],
    queryFn: ({ signal }) => getAuthoringJob(problem.id, id ?? '', signal),
    enabled: open && id !== undefined,
    refetchInterval: ({ state }) => (state.data && isActive(state.data.job.status) ? 1_000 : false),
    gcTime: 0,
    retry: false,
  });
  const job = detail.data?.job;
  const proposal = detail.data?.proposal;
  const result = detail.data?.buildResult;
  const isDraft = problem.visibility === 'PRIVATE' && problem.status === 'ACTIVE';
  const disabledReason = isDraft ? blockedReason : '只有在用的私有题目可以构建或生成提案。';
  const stale = job !== undefined && job.rowVersion !== problem.rowVersion;
  const selectJob = async (updated: AuthoringJobDetail) => {
    setSelected(updated.job.id);
    client.setQueryData([...key, updated.job.id], updated);
    setView('jobs');
    await client.invalidateQueries({ queryKey: [...key, 'list'] });
  };
  const create = useMutation({
    mutationFn: (request: CreateAuthoringJob) => createAuthoringJob(problem.id, request),
    onSuccess: async (updated) => {
      if (updated.job.kind === 'PROPOSAL') setRevision(undefined);
      await selectJob(updated);
    },
  });
  const change = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'cancel' | 'retry' }) =>
      changeAuthoringJob(problem.id, id, action),
    onSuccess: selectJob,
  });
  const apply = useMutation({
    mutationFn: ({ id, digest }: { id: string; digest: string }) =>
      applyAuthoringBuild(problem.id, id, {
        rowVersion: problem.rowVersion,
        testDataDigest: digest,
      }),
    onSuccess: async () => {
      await Promise.all([
        refresh(),
        client.invalidateQueries({ queryKey: problemKeys.limits(problem.id) }),
      ]);
      await client.invalidateQueries({ queryKey: key });
    },
  });

  function queueBuild() {
    setFormError('');
    try {
      const raw: unknown = JSON.parse(plan);
      const parsed = authoringPlanSchema.safeParse(raw);
      if (!parsed.success) {
        const issue = parsed.error.issues[0];
        setFormError(
          issue ? `请检查构建输入字段 ${issue.path.join('.') || 'JSON'}。` : '构建输入格式无效。',
        );
        return;
      }
      create.mutate({ kind: 'BUILD', rowVersion: problem.rowVersion, input: parsed.data });
    } catch {
      setFormError('请输入有效的 JSON 构建计划。');
    }
  }

  function queueProposal() {
    create.mutate({
      kind: 'PROPOSAL',
      rowVersion: problem.rowVersion,
      knowledgePoint: knowledgePoint.trim(),
      difficulty,
      goal: goal.trim(),
      ...(revision
        ? { basedOnProposalId: revision.id, revisionInstructions: revision.instructions.trim() }
        : {}),
    });
  }

  const actionError = create.error ?? change.error;
  const expectedMode = proposal?.languages[0]?.judgeTemplate === null ? 'ACM' : 'CORE';
  const canAdopt =
    Boolean(proposal) && expectedMode === problem.codeMode && !disabledReason && !stale;
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="lg">
        <DialogHeader>
          <DialogTitle>自动出题</DialogTitle>
        </DialogHeader>
        <div className="flex flex-wrap gap-2" role="group" aria-label="出题工作台">
          {(
            [
              ['jobs', '任务记录'],
              ['build', '构建输入'],
              ['proposal', 'AI 提案'],
            ] as const
          ).map(([value, label]) => (
            <Button
              key={value}
              variant={view === value ? 'secondary' : 'ghost'}
              aria-pressed={view === value}
              onClick={() => {
                setView(value);
                setFormError('');
                create.reset();
                change.reset();
              }}
            >
              {label}
            </Button>
          ))}
        </div>
        {disabledReason ? (
          <Text size="sm" tone="muted">
            {disabledReason}
          </Text>
        ) : null}
        {actionError ? (
          <Text role="alert" className="text-danger" size="sm">
            {apiErrorMessage(actionError, '任务请求未完成。')}
          </Text>
        ) : null}

        {view === 'build' ? (
          <section aria-label="构建输入" className="grid min-w-0 gap-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <Text size="sm">构建计划 JSON</Text>
              {plan.length === 0 ? (
                <Button variant="ghost" onClick={() => setPlan(buildTemplate(problem))}>
                  填入题目模板
                </Button>
              ) : null}
            </div>
            <TextEditor aria-label="构建计划 JSON" value={plan} onChange={setPlan} size="compact" />
            {formError ? (
              <Text role="alert" size="sm" className="text-danger">
                {formError}
              </Text>
            ) : null}
            <div className="flex justify-end">
              <Button
                disabled={Boolean(disabledReason)}
                loading={create.isPending}
                onClick={queueBuild}
              >
                开始构建
              </Button>
            </div>
          </section>
        ) : null}

        {view === 'proposal' ? (
          <section aria-label="AI 提案输入" className="grid min-w-0 gap-3">
            <div className="grid gap-2">
              <label htmlFor={knowledgeId} className="text-fg-2 text-sm">
                知识点
              </label>
              <Input
                id={knowledgeId}
                value={knowledgePoint}
                maxLength={160}
                onChange={(event) => setKnowledgePoint(event.target.value)}
              />
            </div>
            <SelectField
              label="难度"
              value={difficulty}
              items={difficulties.map((value) => ({
                value,
                label: difficultyLabel(value),
              }))}
              onValueChange={(value) => {
                const selected = difficulties.find((item) => item === value);
                if (selected) setDifficulty(selected);
              }}
            />
            <TextEditor
              aria-label="出题目标"
              value={goal}
              onChange={setGoal}
              size="compact"
              placeholder="出题目标"
            />
            {revision ? (
              <TextEditor
                aria-label="修订指令"
                value={revision.instructions}
                size="auto"
                placeholder="修订指令"
                onChange={(instructions) => setRevision({ id: revision.id, instructions })}
              />
            ) : null}
            <Text size="sm" tone="muted">
              知识点、难度、出题目标及选中的 AI 提案会发送给 OpenAI。
            </Text>
            <div className="flex flex-wrap justify-end gap-2">
              {revision ? (
                <Button variant="ghost" onClick={() => setRevision(undefined)}>
                  改为新提案
                </Button>
              ) : null}
              <Button
                disabled={
                  Boolean(disabledReason) ||
                  !knowledgePoint.trim() ||
                  !goal.trim() ||
                  Boolean(revision && !revision.instructions.trim())
                }
                loading={create.isPending}
                onClick={queueProposal}
              >
                {revision ? '生成修订提案' : '生成 AI 提案'}
              </Button>
            </div>
          </section>
        ) : null}

        {view === 'jobs' ? (
          <section aria-label="出题任务" className="grid min-w-0 gap-4">
            {jobs.isPending ? <Text size="sm">正在加载出题任务…</Text> : null}
            {jobs.error ? (
              <Text role="alert" size="sm">
                {apiErrorMessage(jobs.error, '无法加载出题任务。')}
              </Text>
            ) : null}
            {jobs.data?.length === 0 ? (
              <Text size="sm" tone="muted">
                还没有出题任务。
              </Text>
            ) : null}
            {jobs.data && jobs.data.length > 0 ? (
              <SelectField
                label="任务"
                value={id ?? ''}
                onValueChange={(value) => {
                  if (value) setSelected(value);
                  change.reset();
                }}
                items={jobs.data.map((item) => ({
                  value: item.id,
                  label: `${item.kind === 'BUILD' ? '验题构建' : 'AI 提案'} · ${statuses[item.status]} · ${new Date(item.createdAt).toLocaleString()}`,
                }))}
              />
            ) : null}
            {id && detail.isPending ? <Text size="sm">正在加载任务结果…</Text> : null}
            {detail.error ? (
              <Text role="alert" size="sm">
                {apiErrorMessage(detail.error, '无法读取任务。')}
              </Text>
            ) : null}
            {job ? (
              <>
                <Text role="status" size="sm">
                  {statuses[job.status]}
                  {job.stage !== 'QUEUED' && job.stage !== 'COMPLETE'
                    ? ` · ${stages[job.stage]}`
                    : null}{' '}
                  · 第 {job.attempt} 次尝试
                </Text>
                {detail.data?.errorCode ? (
                  <Text size="sm" className="text-danger">
                    {errorMessages[detail.data.errorCode]}
                  </Text>
                ) : null}
                {stale && job.appliedAt === null ? (
                  <Text size="sm" tone="muted">
                    题目已保存新修改，应用前请重新验题。
                  </Text>
                ) : null}
                {result ? (
                  <div className="grid min-w-0 gap-2">
                    {result.testcaseCount == null ? null : (
                      <Text size="sm">{result.testcaseCount} 个测试点</Text>
                    )}
                    {result.testDataDigest ? (
                      <details className="text-fg-meta min-w-0 text-xs">
                        <summary className="cursor-pointer">测试数据摘要</summary>
                        <span className="font-mono wrap-anywhere">{result.testDataDigest}</span>
                      </details>
                    ) : null}
                    {result.detail ? (
                      <Text size="sm" className="text-danger">
                        {result.detail}
                      </Text>
                    ) : null}
                    <ul className="grid gap-2">
                      {problem.allowedLanguages.map((language) => {
                        const measured = result.languages.find(
                          (item) => item.languageId === language.id,
                        );
                        const readiness = check?.languages?.find(
                          (item) => item.languageId === language.id,
                        );
                        const missing = readiness?.checks.filter((item) => !item.passed);
                        return (
                          <li key={language.id} className="grid gap-1">
                            <Text size="sm">
                              {language.displayName} ·{' '}
                              {measured?.status === 'PASSED'
                                ? '验题通过'
                                : measured?.status === 'FAILED'
                                  ? '验题失败'
                                  : '未验题'}
                              {measured?.maxCpuNs !== undefined
                                ? ` · ${(measured.maxCpuNs / 1_000_000).toFixed(2)} ms`
                                : ''}
                              {measured?.maxMemoryBytes !== undefined
                                ? ` · ${formatBytes(measured.maxMemoryBytes)}`
                                : ''}
                            </Text>
                            {missing?.map((item) => (
                              <Text key={item.code} size="sm" tone="muted">
                                {item.message}
                              </Text>
                            ))}
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                ) : null}
                {proposal ? (
                  <div className="grid min-w-0 gap-3">
                    <Text>{proposal.title}</Text>
                    <SafeMarkdown value={proposal.statementMarkdown} />
                    <SafeMarkdown value={proposal.inputDescriptionMarkdown} />
                    <SafeMarkdown value={proposal.outputDescriptionMarkdown} />
                    {proposal.constraintsMarkdown ? (
                      <SafeMarkdown value={proposal.constraintsMarkdown} />
                    ) : null}
                    <details>
                      <summary>查看提案样例和语言配置</summary>
                      <TextEditor
                        aria-label="提案样例和语言配置"
                        value={JSON.stringify(
                          { samples: proposal.samples, languages: proposal.languages },
                          null,
                          2,
                        )}
                        onChange={() => {}}
                        readOnly
                        size="compact"
                      />
                    </details>
                    <TextEditor
                      aria-label="提案构建计划"
                      value={JSON.stringify(proposal.input, null, 2)}
                      onChange={() => {}}
                      readOnly
                      size="compact"
                    />
                    {expectedMode !== problem.codeMode ? (
                      <Text size="sm" tone="muted">
                        此提案的代码模式与当前题目不同。
                      </Text>
                    ) : null}
                  </div>
                ) : null}
                <div className="flex flex-wrap items-center justify-end gap-2">
                  {detail.data?.reportAvailable ? (
                    <a
                      className="text-fg-2 hover:text-foreground text-sm underline"
                      href={authoringReportPath(problem.id, job.id)}
                      target="_blank"
                      rel="noreferrer"
                    >
                      查看报告
                    </a>
                  ) : null}
                  {isActive(job.status) ? (
                    <Button
                      variant="secondary"
                      loading={change.isPending}
                      onClick={() => change.mutate({ id: job.id, action: 'cancel' })}
                    >
                      取消任务
                    </Button>
                  ) : null}
                  {(job.status === 'FAILED' || job.status === 'CANCELLED') &&
                  job.attempt < 5 &&
                  !stale ? (
                    <Button
                      variant="secondary"
                      disabled={Boolean(disabledReason)}
                      loading={change.isPending}
                      onClick={() => change.mutate({ id: job.id, action: 'retry' })}
                    >
                      重试
                    </Button>
                  ) : null}
                  {result?.testDataDigest && job.status === 'SUCCEEDED' && !job.appliedAt ? (
                    <Button
                      disabled={Boolean(disabledReason) || stale}
                      onClick={() => setConfirmed('apply')}
                    >
                      应用到草稿
                    </Button>
                  ) : null}
                  {job.appliedAt ? (
                    <Button variant="secondary" onClick={onCalibrate}>
                      进入标定
                    </Button>
                  ) : null}
                  {proposal ? (
                    <>
                      <Button
                        variant="secondary"
                        disabled={Boolean(disabledReason)}
                        onClick={() => {
                          setRevision({ id: job.id, instructions: '' });
                          setView('proposal');
                        }}
                      >
                        修订提案
                      </Button>
                      <Button disabled={!canAdopt} onClick={() => setConfirmed('adopt')}>
                        采用提案
                      </Button>
                    </>
                  ) : null}
                </div>
              </>
            ) : null}
          </section>
        ) : null}
      </DialogContent>
      <ConfirmationDialog
        open={open && confirmed !== undefined}
        onOpenChange={(next) => {
          if (!next) setConfirmed(undefined);
        }}
        title={confirmed === 'apply' ? '应用验题产物' : '采用 AI 提案'}
        description={
          confirmed === 'apply'
            ? '将替换草稿的当前测试数据。'
            : '将替换当前草稿的题面、样例和语言配置。'
        }
        confirmLabel={confirmed === 'apply' ? '应用到草稿' : '采用提案'}
        loading={apply.isPending}
        onConfirm={async () => {
          if (confirmed === 'apply' && job && result?.testDataDigest) {
            await apply.mutateAsync({ id: job.id, digest: result.testDataDigest });
          } else if (proposal && canAdopt) {
            onAdopt(proposal);
            setPlan(JSON.stringify(proposal.input, null, 2));
            setView('build');
          }
        }}
      />
    </Dialog>
  );
}
