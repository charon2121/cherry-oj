import 'monaco-editor/languages/definitions/cpp/register';

import Editor, { loader } from '@monaco-editor/react';
import { useForm } from '@tanstack/react-form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { Archive, Download, RotateCcw, Save, Upload } from 'lucide-react';
import * as monaco from 'monaco-editor/editor/editor.api';
import { useEffect, useRef, useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Panel } from '@/components/ui/card';
import { FormField } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Cluster, Container, Section, Stack } from '@/components/ui/layout';
import { SelectField } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Heading, Text } from '@/components/ui/typography';
import type { AdminProblemVersion, ProblemDifficulty, ProblemSample } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';

import {
  archiveProblem,
  bindTestData,
  calibrate,
  createRevision,
  deleteVersion,
  deployTestData,
  getAdminProblem,
  getPublishCheck,
  getVersion,
  listTestData,
  problemKeys,
  publish,
  updateProblem,
  updateVersion,
  uploadTestData,
} from '../api/problems-api';
import { SafeMarkdown } from './safe-markdown';

loader.config({ monaco });

export function AdminProblemWorkbench({
  problemId,
  versionId,
}: {
  problemId: string;
  versionId: string;
}) {
  const version = useQuery({
    queryKey: problemKeys.version(problemId, versionId),
    queryFn: ({ signal }) => getVersion(problemId, versionId, signal),
  });
  const problem = useQuery({
    queryKey: problemKeys.adminProblem(problemId),
    queryFn: ({ signal }) => getAdminProblem(problemId, signal),
  });
  if (version.isPending || problem.isPending)
    return (
      <Container>
        <Section className="py-10">
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
  if (version.isError || problem.isError)
    return (
      <Container>
        <Section className="py-10">
          <AsyncState
            variant="error"
            size="page"
            title="工作台无法加载"
            action={
              <Button
                variant="secondary"
                onClick={() => {
                  void version.refetch();
                  void problem.refetch();
                }}
              >
                <RotateCcw aria-hidden="true" />
                重新加载
              </Button>
            }
          >
            没有覆盖本地数据，请检查权限或链接。
          </AsyncState>
        </Section>
      </Container>
    );
  return (
    <WorkbenchEditor
      key={`${version.data.id}:${version.data.rowVersion}`}
      problem={problem.data}
      version={version.data}
    />
  );
}

function WorkbenchEditor({
  problem,
  version,
}: {
  problem: Awaited<ReturnType<typeof getAdminProblem>>;
  version: AdminProblemVersion;
}) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string>();
  const [referenceSource, setReferenceSource] = useState('');
  const [selectedTestDataId, setSelectedTestDataId] = useState(version.testDataVersion?.id ?? '');
  const [uploadState, setUploadState] = useState<string>();
  const uploadController = useRef<AbortController | undefined>(undefined);
  useEffect(
    () => () => {
      uploadController.current?.abort();
      setReferenceSource('');
    },
    [],
  );

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: problemKeys.adminProblem(problem.id) }),
      queryClient.invalidateQueries({ queryKey: problemKeys.version(problem.id, version.id) }),
      queryClient.invalidateQueries({ queryKey: problemKeys.testData(problem.id) }),
      queryClient.invalidateQueries({ queryKey: problemKeys.publishCheck(problem.id, version.id) }),
    ]);
  };
  const form = useForm({
    defaultValues: {
      title: version.title,
      statementMarkdown: version.statementMarkdown,
      inputDescriptionMarkdown: version.inputDescriptionMarkdown,
      outputDescriptionMarkdown: version.outputDescriptionMarkdown,
      constraintsMarkdown: version.constraintsMarkdown ?? '',
      hintMarkdown: version.hintMarkdown ?? '',
      difficulty: version.difficulty,
      tags: version.tags.join(', '),
      samplesJson: JSON.stringify(version.samples, null, 2),
      starterCode: version.allowedLanguages[0].starterCode,
      changeSummary: version.changeSummary ?? '',
    },
    onSubmit: async ({ value }) => {
      setFormError(undefined);
      let samples: ProblemSample[];
      try {
        const parsed: unknown = JSON.parse(value.samplesJson);
        if (!Array.isArray(parsed)) throw new Error('not array');
        samples = parsed as ProblemSample[];
      } catch {
        setFormError('样例必须是合法的 JSON 数组。');
        return;
      }
      await save.mutateAsync({ ...value, samples });
    },
  });
  const save = useMutation({
    mutationFn: (value: typeof form.state.values & { samples: ProblemSample[] }) =>
      updateVersion(problem.id, version.id, {
        title: value.title,
        statementMarkdown: value.statementMarkdown,
        inputDescriptionMarkdown: value.inputDescriptionMarkdown,
        outputDescriptionMarkdown: value.outputDescriptionMarkdown,
        constraintsMarkdown: value.constraintsMarkdown || null,
        hintMarkdown: value.hintMarkdown || null,
        difficulty: value.difficulty,
        tags: value.tags
          .split(',')
          .map((tag) => tag.trim())
          .filter(Boolean),
        samples: value.samples,
        starterCode: value.starterCode,
        changeSummary: value.changeSummary || null,
        rowVersion: version.rowVersion,
      }),
    onSuccess: refresh,
  });
  const testData = useQuery({
    queryKey: problemKeys.testData(problem.id),
    queryFn: ({ signal }) => listTestData(problem.id, signal),
  });
  const upload = useMutation({
    mutationFn: ({ file, signal }: { file: File; signal: AbortSignal }) =>
      uploadTestData(problem.id, file, signal),
    onMutate: () => setUploadState('正在上传并校验 ZIP…'),
    onSuccess: async (data) => {
      setUploadState(`测试数据已就绪，共 ${data.caseCount ?? 0} 组。`);
      setSelectedTestDataId(data.id);
      await refresh();
    },
    onError: (error) =>
      setUploadState(
        error instanceof ApiError && error.kind === 'aborted'
          ? '上传已取消。'
          : '上传或 ZIP 校验失败。',
      ),
  });
  const bind = useMutation({
    mutationFn: (testDataVersionId: string) =>
      bindTestData(problem.id, version.id, { testDataVersionId, rowVersion: version.rowVersion }),
    onSuccess: refresh,
  });
  const deploy = useMutation({
    mutationFn: () => {
      const selected = testData.data?.find((item) => item.id === selectedTestDataId);
      if (!selected?.contentSha256) throw new Error('请选择 READY 测试数据。');
      return deployTestData(problem.id, version.id, {
        testDataVersionId: selected.id,
        expectedSha256: selected.contentSha256,
        rowVersion: version.rowVersion,
      });
    },
    onSuccess: refresh,
  });
  const [limits, setLimits] = useState({
    cpuNs: '1000000000',
    memoryBytes: '268435456',
    clockNs: '',
  });
  const calibration = useMutation({
    mutationFn: () =>
      calibrate(problem.id, version.id, {
        languageId: 'cpp',
        cpuNs: Number(limits.cpuNs),
        memoryBytes: Number(limits.memoryBytes),
        clockNs: limits.clockNs ? Number(limits.clockNs) : null,
        referenceSource,
        rowVersion: version.rowVersion,
      }),
    onSuccess: async () => {
      setReferenceSource('');
      await refresh();
    },
  });
  const check = useQuery({
    queryKey: problemKeys.publishCheck(problem.id, version.id),
    queryFn: ({ signal }) => getPublishCheck(problem.id, version.id, signal),
    enabled: false,
    retry: false,
  });
  const publishing = useMutation({
    mutationFn: () => publish(problem.id, version.id, version.rowVersion),
    onSuccess: refresh,
  });
  const revision = useMutation({
    mutationFn: (reuseTestData: boolean) =>
      createRevision(problem.id, { rowVersion: problem.rowVersion, reuseTestData }),
    onSuccess: async (created) => {
      await refresh();
      await navigate({
        to: '/admin/problems/$problemId/versions/$versionId',
        params: { problemId: problem.id, versionId: created.id },
      });
    },
  });
  const archive = useMutation({
    mutationFn: () => archiveProblem(problem.id, problem.rowVersion),
    onSuccess: refresh,
  });
  const remove = useMutation({
    mutationFn: () => deleteVersion(problem.id, version.id, version.rowVersion),
    onSuccess: async () => {
      await refresh();
      await navigate({ to: '/admin/problems', search: { page: 1, q: '', status: 'ALL' } });
    },
  });
  const visibility = useMutation({
    mutationFn: () =>
      updateProblem(problem.id, {
        slug: problem.slug,
        visibility: problem.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC',
        rowVersion: problem.rowVersion,
      }),
    onSuccess: refresh,
  });
  const conflict =
    save.error instanceof ApiError &&
    (save.error.status === 409 || save.error.code?.includes('CONFLICT'));

  return (
    <Container>
      <Section className="py-8">
        <Cluster gap={4} justify="between" className="items-end">
          <Stack gap={2}>
            <Link
              to="/admin/problems"
              search={{ page: 1, q: '', status: 'ALL' }}
              className="text-brand text-sm underline"
            >
              返回题目管理
            </Link>
            <Text size="sm" tone="muted">
              {problem.slug} · v{version.versionNo}
            </Text>
            <Heading level={1} size="2xl">
              {version.title}
            </Heading>
            <Cluster gap={2}>
              <Badge>{version.status}</Badge>
              <Badge>{problem.visibility}</Badge>
              <Badge>{version.codeMode}</Badge>
            </Cluster>
          </Stack>
          <Cluster gap={2}>
            <Button
              variant="secondary"
              loading={visibility.isPending}
              onClick={() => visibility.mutate()}
            >
              {problem.visibility === 'PUBLIC' ? '转为私有' : '转为公开'}
            </Button>
            <Button loading={save.isPending} onClick={() => void form.handleSubmit()}>
              <Save aria-hidden="true" />
              保存草稿
            </Button>
          </Cluster>
        </Cluster>

        {save.isError ? (
          <Panel className="mt-4" role="alert">
            <Heading level={2} size="lg">
              {conflict ? '保存冲突，本地输入仍保留' : '保存失败'}
            </Heading>
            <Text className="text-danger mt-2" size="sm">
              {conflict
                ? '服务端版本已变化。请在复制本地内容后重载最新版本，再决定如何合并。'
                : '请检查内容后重试；结果不明时先重新加载。'}
            </Text>
            <Button className="mt-3" variant="secondary" onClick={() => void refresh()}>
              重载服务端版本
            </Button>
          </Panel>
        ) : null}
        {formError ? (
          <Text className="text-danger mt-4" role="alert">
            {formError}
          </Text>
        ) : null}

        <form
          onSubmit={(event) => {
            event.preventDefault();
            void form.handleSubmit();
          }}
        >
          <Panel className="mt-6">
            <Heading level={2} size="lg">
              题面与样例
            </Heading>
            <div className="mt-4 grid gap-4">
              <form.Field name="title">
                {(field) => (
                  <FormField label="标题" required>
                    <Input
                      value={field.state.value}
                      onChange={(event) => field.handleChange(event.target.value)}
                    />
                  </FormField>
                )}
              </form.Field>
              <div className="grid gap-4 lg:grid-cols-2">
                <form.Field name="statementMarkdown">
                  {(field) => (
                    <FormField label="题目正文 Markdown" required>
                      <Textarea
                        className="min-h-72 font-mono"
                        value={field.state.value}
                        onChange={(event) => field.handleChange(event.target.value)}
                      />
                    </FormField>
                  )}
                </form.Field>
                <form.Subscribe selector={(state) => state.values.statementMarkdown}>
                  {(value) => (
                    <div>
                      <Text size="sm" tone="secondary">
                        安全预览
                      </Text>
                      <div className="border-border mt-2 min-h-72 rounded-md border p-4">
                        <SafeMarkdown value={value} />
                      </div>
                    </div>
                  )}
                </form.Subscribe>
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                <form.Field name="inputDescriptionMarkdown">
                  {(field) => (
                    <FormField label="输入说明">
                      <Textarea
                        value={field.state.value}
                        onChange={(event) => field.handleChange(event.target.value)}
                      />
                    </FormField>
                  )}
                </form.Field>
                <form.Field name="outputDescriptionMarkdown">
                  {(field) => (
                    <FormField label="输出说明">
                      <Textarea
                        value={field.state.value}
                        onChange={(event) => field.handleChange(event.target.value)}
                      />
                    </FormField>
                  )}
                </form.Field>
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                <form.Field name="constraintsMarkdown">
                  {(field) => (
                    <FormField label="数据范围">
                      <Textarea
                        value={field.state.value}
                        onChange={(event) => field.handleChange(event.target.value)}
                      />
                    </FormField>
                  )}
                </form.Field>
                <form.Field name="hintMarkdown">
                  {(field) => (
                    <FormField label="提示">
                      <Textarea
                        value={field.state.value}
                        onChange={(event) => field.handleChange(event.target.value)}
                      />
                    </FormField>
                  )}
                </form.Field>
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                <form.Field name="difficulty">
                  {(field) => (
                    <SelectField
                      label="难度"
                      value={field.state.value}
                      onValueChange={(value) => field.handleChange(value as ProblemDifficulty)}
                      items={[
                        { value: 'UNRATED', label: '未评级' },
                        { value: 'EASY', label: '简单' },
                        { value: 'MEDIUM', label: '中等' },
                        { value: 'HARD', label: '困难' },
                      ]}
                    />
                  )}
                </form.Field>
                <form.Field name="tags">
                  {(field) => (
                    <FormField label="标签" description="用英文逗号分隔">
                      <Input
                        value={field.state.value}
                        onChange={(event) => field.handleChange(event.target.value)}
                      />
                    </FormField>
                  )}
                </form.Field>
              </div>
              <form.Field name="samplesJson">
                {(field) => (
                  <FormField
                    label="样例 JSON"
                    description="ordinal、input、output、explanationMarkdown 数组"
                  >
                    <Textarea
                      className="min-h-56 font-mono"
                      value={field.state.value}
                      onChange={(event) => field.handleChange(event.target.value)}
                    />
                  </FormField>
                )}
              </form.Field>
              <form.Field name="changeSummary">
                {(field) => (
                  <FormField label="修改说明">
                    <Input
                      value={field.state.value}
                      onChange={(event) => field.handleChange(event.target.value)}
                    />
                  </FormField>
                )}
              </form.Field>
            </div>
          </Panel>
          <Panel className="mt-6">
            <Heading level={2} size="lg">
              C++ 起始代码
            </Heading>
            <form.Field name="starterCode">
              {(field) => (
                <div className="border-border mt-4 overflow-hidden rounded-md border">
                  <Editor
                    height="24rem"
                    language="cpp"
                    value={field.state.value}
                    onChange={(value) => field.handleChange(value ?? '')}
                    options={{
                      minimap: { enabled: false },
                      automaticLayout: true,
                      scrollBeyondLastLine: false,
                      wordWrap: 'on',
                    }}
                  />
                </div>
              )}
            </form.Field>
          </Panel>
        </form>

        <Panel className="mt-6">
          <Heading level={2} size="lg">
            测试数据
          </Heading>
          <Text className="mt-2" size="sm" tone="muted">
            只接受安全命名且 .in/.out 成对的 ZIP。正文不会进入浏览器缓存或 URL。
          </Text>
          <div className="mt-4 grid gap-3 sm:grid-cols-[minmax(14rem,1fr)_auto_auto]">
            <FormField label="上传测试数据 ZIP">
              <Input
                type="file"
                accept=".zip,application/zip"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (!file) return;
                  uploadController.current?.abort();
                  const controller = new AbortController();
                  uploadController.current = controller;
                  upload.mutate({ file, signal: controller.signal });
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
            <Button
              className="self-end"
              variant="secondary"
              disabled={!selectedTestDataId || bind.isPending}
              onClick={() => bind.mutate(selectedTestDataId)}
            >
              <Upload aria-hidden="true" />
              绑定所选版本
            </Button>
          </div>
          {uploadState ? (
            <Text className="mt-3" role="status" aria-live="polite">
              {uploadState}
            </Text>
          ) : null}
          {upload.isPending ? (
            <progress className="mt-3 w-full" aria-label="测试数据正在上传并校验" />
          ) : null}
          {testData.isPending ? (
            <AsyncState
              className="mt-4"
              variant="loading"
              size="inline"
              title="正在读取测试数据版本…"
              progressLabel="正在读取测试数据版本…"
            >
              {null}
            </AsyncState>
          ) : null}
          {testData.isError ? (
            <Text className="text-danger mt-4" role="alert">
              测试数据列表加载失败。
            </Text>
          ) : null}
          <div className="mt-4 grid gap-2">
            {testData.data?.map((item) => (
              <label
                key={item.id}
                className="bg-surface-subtle border-border flex min-w-0 items-center gap-3 rounded-md border p-3"
              >
                <input
                  type="radio"
                  name="test-data"
                  value={item.id}
                  checked={selectedTestDataId === item.id}
                  onChange={() => setSelectedTestDataId(item.id)}
                />
                <span className="min-w-0 flex-1 wrap-anywhere">
                  <strong>{item.status}</strong> · {item.caseCount ?? '—'} 组 ·{' '}
                  {item.totalBytes ?? '—'} bytes
                  <br />
                  <span className="text-muted-foreground font-mono text-sm">
                    {item.contentSha256 ?? item.errorMessage ?? item.id}
                  </span>
                </span>
                <a
                  className={buttonVariants({ size: 'sm', variant: 'secondary' })}
                  href={`/api/admin/problems/${problem.id}/test-data/${item.id}/download`}
                  download
                >
                  <Download aria-hidden="true" />
                  下载
                </a>
              </label>
            ))}
          </div>
        </Panel>

        <Panel className="mt-6">
          <Heading level={2} size="lg">
            部署与参考程序校准
          </Heading>
          <Cluster className="mt-4" gap={3}>
            <Button
              variant="secondary"
              loading={deploy.isPending}
              disabled={!selectedTestDataId}
              onClick={() => deploy.mutate()}
            >
              部署测试数据
            </Button>
            {deploy.data ? (
              <Badge variant={deploy.data.status === 'READY' ? 'success' : 'info'}>
                {deploy.data.environmentName} · {deploy.data.status}
              </Badge>
            ) : null}
          </Cluster>
          {deploy.isError ? (
            <Text className="text-danger mt-3" role="alert">
              部署失败或结果不明，请刷新服务端状态后再试。
            </Text>
          ) : null}
          <div className="mt-5 grid gap-3 md:grid-cols-3">
            <FormField label="CPU 上限（ns）">
              <Input
                inputMode="numeric"
                value={limits.cpuNs}
                onChange={(event) =>
                  setLimits((current) => ({ ...current, cpuNs: event.target.value }))
                }
              />
            </FormField>
            <FormField label="内存上限（bytes）">
              <Input
                inputMode="numeric"
                value={limits.memoryBytes}
                onChange={(event) =>
                  setLimits((current) => ({ ...current, memoryBytes: event.target.value }))
                }
              />
            </FormField>
            <FormField label="时钟上限（ns，可选）">
              <Input
                inputMode="numeric"
                value={limits.clockNs}
                onChange={(event) =>
                  setLimits((current) => ({ ...current, clockNs: event.target.value }))
                }
              />
            </FormField>
          </div>
          <Text className="mt-4" size="sm" tone="secondary">
            参考程序仅保存在当前组件内存，提交成功或离开页面即清空。
          </Text>
          <div className="border-border mt-2 overflow-hidden rounded-md border">
            <Editor
              height="22rem"
              language="cpp"
              value={referenceSource}
              onChange={(value) => setReferenceSource(value ?? '')}
              options={{
                minimap: { enabled: false },
                automaticLayout: true,
                scrollBeyondLastLine: false,
              }}
            />
          </div>
          <Button
            className="mt-4"
            loading={calibration.isPending}
            disabled={!referenceSource.trim()}
            onClick={() => calibration.mutate()}
          >
            运行参考程序并校准
          </Button>
          {calibration.data ? (
            <Text className="mt-3" role="status">
              校准状态：{calibration.data.status}，参考源码已从页面内存清除。
            </Text>
          ) : null}
          {calibration.isError ? (
            <Text className="text-danger mt-3" role="alert">
              校准失败，草稿仍可继续编辑。
            </Text>
          ) : null}
        </Panel>

        <Panel className="mt-6">
          <Heading level={2} size="lg">
            发布与版本
          </Heading>
          <Cluster className="mt-4" gap={3}>
            <Button
              variant="secondary"
              loading={check.isFetching}
              onClick={() => void check.refetch()}
            >
              运行发布检查
            </Button>
            <Button
              disabled={!check.data?.ready}
              loading={publishing.isPending}
              onClick={() => {
                if (
                  window.confirm(
                    `确认发布 ${problem.slug} v${version.versionNo}？公开题库将立即切换到此版本。`,
                  )
                )
                  publishing.mutate();
              }}
            >
              确认发布
            </Button>
          </Cluster>
          {check.data ? (
            <ul className="mt-4 grid gap-2">
              {check.data.checks.map((item) => (
                <li key={item.code} className="flex gap-2">
                  <Badge variant={item.passed ? 'success' : 'danger'}>
                    {item.passed ? '通过' : '未通过'}
                  </Badge>
                  <span>{item.message}</span>
                </li>
              ))}
            </ul>
          ) : null}
          {check.isError ? (
            <Text className="text-danger mt-3" role="alert">
              发布检查失败，请先刷新版本状态。
            </Text>
          ) : null}
          {publishing.isError ? (
            <Text className="text-danger mt-3" role="alert">
              发布失败，旧公开版本保持不变。
            </Text>
          ) : null}
          <Cluster className="border-border mt-6 border-t pt-5" gap={2}>
            <Button
              variant="secondary"
              loading={revision.isPending}
              onClick={() => {
                if (window.confirm('确认创建新修订并复用当前测试数据绑定？')) {
                  revision.mutate(true);
                }
              }}
            >
              创建修订并复用数据
            </Button>
            <Button
              variant="secondary"
              disabled={revision.isPending}
              onClick={() => {
                if (window.confirm('确认创建不绑定测试数据的新修订？')) revision.mutate(false);
              }}
            >
              创建空修订
            </Button>
            <Button
              variant="secondary"
              loading={archive.isPending}
              onClick={() => {
                if (window.confirm(`确认归档题目 ${problem.slug}？`)) archive.mutate();
              }}
            >
              <Archive aria-hidden="true" />
              归档题目
            </Button>
            {version.status === 'DRAFT' ? (
              <Button
                variant="danger"
                loading={remove.isPending}
                onClick={() => {
                  if (window.confirm(`确认删除未发布草稿 v${version.versionNo}？此操作不可撤销。`))
                    remove.mutate();
                }}
              >
                删除草稿
              </Button>
            ) : null}
          </Cluster>
        </Panel>
      </Section>
    </Container>
  );
}
