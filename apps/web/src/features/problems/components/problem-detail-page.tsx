import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { ArrowLeft, BookOpen, Code2, Play } from 'lucide-react';
import { type Ref, useRef, useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Button, buttonVariants } from '@/components/ui/button';
import { Container, Section } from '@/components/ui/layout';
import { WorkbenchPageTemplate } from '@/components/ui/page-templates';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { TextEditor } from '@/components/ui/text-editor';
import { Heading } from '@/components/ui/typography';
import { sessionQueryOptions } from '@/features/auth/api/session-query';
import { CustomRunWorkbench } from '@/features/custom-runs/custom-run-workbench';
import { SubmissionHistoryPanel } from '@/features/submissions/submission-history-panel';
import type { ProblemDetail } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';
import { cn } from '@/lib/utils';

import { problemQuery } from '../api/problems-api';
import { useWorkbenchMedia } from '../hooks/use-workbench-media';
import { DifficultyIcon, difficultyLabel } from './difficulty-icon';
import { DraftEditor, type HistoryEditorHandle, SourceEditor } from './problem-source-editor';
import { SafeMarkdown } from './safe-markdown';
import { SourceCopyButton } from './source-copy-button';

export function ProblemDetailPage({ slug }: { slug: string }) {
  const problem = useQuery(problemQuery(slug));
  if (!problem.data) {
    const notFound =
      problem.error instanceof ApiError && problem.error.code === 'PROBLEM_NOT_FOUND';
    return (
      <Container className="h-full overflow-y-auto">
        <Section>
          <AsyncState
            {...(problem.isPending
              ? { variant: 'loading' as const, progressLabel: '正在加载题目…' }
              : { variant: notFound ? ('empty' as const) : ('error' as const) })}
            size="page"
            title={
              problem.isPending
                ? '正在加载题目…'
                : notFound
                  ? '题目不存在或尚未公开'
                  : '题目暂时无法加载'
            }
            action={
              problem.isPending ? undefined : (
                <Link
                  to="/problems"
                  search={{ sort: 'UPDATED_DESC', size: 20 }}
                  className={buttonVariants({ variant: 'secondary' })}
                >
                  返回题库
                </Link>
              )
            }
          >
            {problem.isPending ? null : notFound ? (
              '请检查链接，或返回题库选择其它题目。'
            ) : (
              <Button variant="secondary" onClick={() => void problem.refetch()}>
                重试
              </Button>
            )}
          </AsyncState>
        </Section>
      </Container>
    );
  }
  return (
    <ProblemWorkbench
      key={problem.data.problemId}
      data={problem.data}
      unavailable={problem.isError}
    />
  );
}

function ProblemWorkbench({ data, unavailable }: { data: ProblemDetail; unavailable: boolean }) {
  // 题目没有版本：管理员改了题面，学生刷新后看到的就是新题面；本机草稿按题目保存，不受影响。
  const [pane, setPane] = useState('statement');
  const wide = useWorkbenchMedia('(min-width: 1024px)');
  const historyEditor = useRef<HistoryEditorHandle>(null);
  const historySearch = useSearch({ from: '/_site/problems/$slug' });
  const navigate = useNavigate({ from: '/problems/$slug' });
  return (
    <WorkbenchPageTemplate
      variant="coding"
      title={data.title}
      titleVisible={false}
      statusBar={
        <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1">
          <Link
            to="/problems"
            search={{ sort: 'UPDATED_DESC', size: 20 }}
            className={buttonVariants({ variant: 'ghost', size: 'sm', className: 'shrink-0 px-2' })}
          >
            <ArrowLeft aria-hidden="true" />
            题库
          </Link>
          <span className="min-w-0 flex-1 text-sm break-words">{data.title}</span>
          <span className="text-fg-meta max-w-full shrink-0 font-mono text-xs break-all">
            {data.slug}
          </span>
        </div>
      }
    >
      <Tabs
        value={pane}
        onValueChange={(value) => setPane(String(value))}
        className="h-full min-h-0"
      >
        <TabsList aria-label="答题区域" hidden={wide}>
          <TabsTrigger value="statement">
            <BookOpen aria-hidden="true" />
            题目
          </TabsTrigger>
          <TabsTrigger value="code">
            <Code2 aria-hidden="true" />
            代码
          </TabsTrigger>
        </TabsList>
        <div className="grid min-h-0 min-w-0 flex-1 lg:grid-cols-2">
          <TabsContent
            keepMounted
            value="statement"
            hidden={!wide && pane !== 'statement'}
            inert={!wide && pane !== 'statement'}
            role={wide ? 'region' : 'tabpanel'}
            aria-label={wide ? '题目' : undefined}
            tabIndex={0}
            className="overflow-y-auto overscroll-contain"
          >
            <Tabs
              value={historySearch.tab ?? 'statement'}
              onValueChange={(value) => {
                void navigate({
                  search: (previous) => ({
                    ...previous,
                    tab: value === 'submissions' ? 'submissions' : 'statement',
                  }),
                  replace: true,
                });
              }}
            >
              <TabsList aria-label="题目内容">
                <TabsTrigger value="statement">题目描述</TabsTrigger>
                <TabsTrigger value="submissions">提交记录</TabsTrigger>
              </TabsList>
              <TabsContent value="statement">
                <>
                  {unavailable ? (
                    <p role="alert" className="text-fg-muted p-4">
                      题目状态暂时无法确认，请刷新页面。历史代码仍可复制。
                    </p>
                  ) : (
                    <ProblemStatement data={data} />
                  )}
                </>
              </TabsContent>
              <TabsContent value="submissions">
                <SubmissionHistoryPanel
                  problem={data}
                  canLoad={!unavailable}
                  onLoad={(value) => {
                    if (!unavailable) historyEditor.current?.requestLoad(value);
                  }}
                />
              </TabsContent>
            </Tabs>
          </TabsContent>
          <TabsContent
            keepMounted
            value="code"
            hidden={!wide && pane !== 'code'}
            inert={!wide && pane !== 'code'}
            role={wide ? 'region' : 'tabpanel'}
            aria-label={wide ? '代码' : undefined}
            tabIndex={0}
            className={cn(
              'border-border-soft overflow-y-auto overscroll-contain lg:border-l',
              !wide && pane !== 'code' ? 'hidden' : 'flex flex-col',
            )}
          >
            <ProblemCode
              key={data.problemId}
              data={data}
              historyRef={historyEditor}
              unavailable={unavailable}
            />
          </TabsContent>
        </div>
      </Tabs>
    </WorkbenchPageTemplate>
  );
}

function ProblemStatement({ data }: { data: ProblemDetail }) {
  return (
    <article className="text-fg-2 min-w-0 space-y-6 p-4 sm:p-6">
      <div className="text-fg-meta flex flex-wrap items-center gap-x-3 gap-y-2 text-xs">
        <span className="text-fg-muted flex items-center gap-2">
          <DifficultyIcon difficulty={data.difficulty} />
          {difficultyLabel(data.difficulty)}
        </span>
        <span>{data.codeMode}</span>
        {data.tags.map((tag) => (
          <span key={tag}>{tag}</span>
        ))}
      </div>
      <SafeMarkdown value={data.statementMarkdown} />
      <section>
        <Heading level={2} size="base">
          输入说明
        </Heading>
        <SafeMarkdown className="mt-3" value={data.inputDescriptionMarkdown} />
      </section>
      <section>
        <Heading level={2} size="base">
          输出说明
        </Heading>
        <SafeMarkdown className="mt-3" value={data.outputDescriptionMarkdown} />
      </section>
      {data.constraintsMarkdown ? (
        <section>
          <Heading level={2} size="base">
            数据范围
          </Heading>
          <SafeMarkdown className="mt-3" value={data.constraintsMarkdown} />
        </section>
      ) : null}
      <section>
        <Heading level={2} size="base">
          样例
        </Heading>
        <div className="mt-3 space-y-6">
          {data.samples.map((sample) => (
            <section
              key={sample.ordinal}
              aria-label={`样例 ${sample.ordinal}`}
              className="min-w-0 space-y-3"
            >
              <span className="text-fg-meta text-xs">样例 {sample.ordinal}</span>
              {(
                [
                  { label: '输入', value: sample.input },
                  { label: '输出', value: sample.output },
                ] as const
              ).map(({ label, value }) => (
                <div key={label} className="bg-surface-subtle min-w-0 rounded-sm px-3 py-2">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-fg-muted text-xs">{label}</span>
                    <SourceCopyButton value={value} label={`复制样例 ${sample.ordinal} ${label}`} />
                  </div>
                  <pre className="overflow-x-auto py-1 font-mono text-sm">
                    <code>{value}</code>
                  </pre>
                </div>
              ))}
              {sample.explanationMarkdown ? (
                <SafeMarkdown value={sample.explanationMarkdown} />
              ) : null}
            </section>
          ))}
        </div>
      </section>
      {data.hintMarkdown ? (
        <section>
          <Heading level={2} size="base">
            提示
          </Heading>
          <SafeMarkdown className="mt-3" value={data.hintMarkdown} />
        </section>
      ) : null}
    </article>
  );
}

function ProblemCode({
  data,
  historyRef,
  unavailable,
}: {
  data: ProblemDetail;
  historyRef: Ref<HistoryEditorHandle>;
  unavailable: boolean;
}) {
  const session = useQuery({ ...sessionQueryOptions(), refetchInterval: 15_000 });
  const language =
    data.allowedLanguages.find((item) => item.id === 'cpp') ?? data.allowedLanguages[0];
  const supported = data.codeMode === 'ACM' && language?.id === 'cpp';
  const user = session.data?.authenticated ? session.data.user : undefined;
  const editableIdentity =
    user && user.status === 'ACTIVE' && !user.passwordChangeRequired && supported;
  const notice = session.isPending
    ? '正在确认登录状态…'
    : session.isError
      ? '登录状态暂时无法确认，代码已暂停编辑。请重试。'
      : user?.passwordChangeRequired
        ? '请修改密码后继续编写代码。'
        : user?.status === 'DISABLED'
          ? '当前账号不可用，代码为只读预览。'
          : !user
            ? '登录后可编写代码，并在此浏览器保存草稿。'
            : !supported
              ? '当前页面暂不支持此题目的编码模式或语言。'
              : null;
  return (
    <>
      <div className="border-border-soft text-fg-muted flex shrink-0 flex-wrap items-center gap-3 border-b px-4 py-2 text-xs">
        <Code2 className="size-4" aria-hidden="true" />
        <span className="text-foreground">{language?.displayName ?? '无可用语言'}</span>
        <span>{data.codeMode}</span>
        <span className="text-fg-meta ml-auto">代码</span>
      </div>
      {notice ? (
        <div
          className="border-border-soft text-fg-2 flex shrink-0 flex-wrap items-center gap-2 border-b px-4 py-2 text-xs"
          role="status"
        >
          <span>{notice}</span>
          {session.isError ? (
            <Button size="sm" variant="secondary" onClick={() => void session.refetch()}>
              重试登录状态
            </Button>
          ) : user?.passwordChangeRequired ? (
            <Link to="/account/password" className={buttonVariants({ size: 'sm' })}>
              修改密码后继续
            </Link>
          ) : !session.isPending && !user ? (
            <Link
              to="/login"
              search={{ returnTo: `/problems/${data.slug}` }}
              className={buttonVariants({ size: 'sm' })}
            >
              登录后编写代码
            </Link>
          ) : null}
        </div>
      ) : null}
      {editableIdentity && language ? (
        <DraftEditor
          key={`${user.id}:${data.problemId}:${language.id}`}
          userId={user.id}
          problemId={data.problemId}
          languageId={language.id}
          starterCode={language.starterCode}
          readOnly={session.isError || unavailable}
          historyRef={historyRef}
          renderSubmission={(source) => (
            <CustomRunWorkbench
              userId={user.id}
              problem={data}
              source={source}
              disabled={session.isError || unavailable}
            />
          )}
        />
      ) : (
        <SourceEditor value={language?.starterCode ?? ''} onChange={() => undefined} readOnly />
      )}
      {!editableIdentity ? (
        <div className="border-border-soft bg-panel shrink-0 space-y-2 border-t px-4 py-3">
          <Button size="sm" variant="secondary" disabled aria-describedby="judging-unavailable">
            <Play aria-hidden="true" />
            运行
          </Button>
          <TextEditor
            value=""
            onChange={() => undefined}
            readOnly
            language="plain"
            size="compact"
            aria-label="自定义输入数据"
          />
          <p id="judging-unavailable" className="text-fg-muted text-xs">
            登录并选择支持的题目后，可自定义运行和正式提交。
          </p>
        </div>
      ) : null}
    </>
  );
}
