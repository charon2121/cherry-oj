import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { BookOpen, ChevronRight, Code2 } from 'lucide-react';
import { type ReactNode, type Ref, useRef, useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Button, buttonVariants } from '@/components/ui/button';
import { Container, Section } from '@/components/ui/layout';
import { WorkbenchPageTemplate } from '@/components/ui/page-templates';
import { SplitPane } from '@/components/ui/split-pane';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Heading } from '@/components/ui/typography';
import { sessionQueryOptions } from '@/features/auth/api/session-query';
import { CustomRunWorkbench } from '@/features/custom-runs/custom-run-workbench';
import { SubmissionHistoryPanel } from '@/features/submissions/submission-history-panel';
import type { ProblemDetail } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';
import { cn } from '@/lib/utils';

import { problemQuery } from '../api/problems-api';
import { useWorkbenchMedia } from '../hooks/use-workbench-media';
import { codeModeLabels } from '../lib/code-mode';
import { DifficultyIcon, difficultyLabel } from './difficulty-icon';
import {
  DraftEditor,
  type HistoryEditorHandle,
  Pane,
  PaneHeader,
  SourceEditor,
} from './problem-source-editor';
import { SafeMarkdown } from './safe-markdown';
import { CopyIconButton } from './source-copy-button';

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

  // linear.app 详情页顶栏：面包屑在左（上一级可点，当前项最亮），标识在右。
  const breadcrumb = (
    <nav aria-label="面包屑" className="flex h-7 min-w-0 items-center gap-1.5 text-sm">
      <Link
        to="/problems"
        search={{ sort: 'UPDATED_DESC', size: 20 }}
        className="text-fg-meta hover:text-foreground focus-visible:outline-ring shrink-0 rounded-sm px-1 outline-none focus-visible:outline-1"
      >
        题库
      </Link>
      <ChevronRight className="text-fg-disabled size-3.5 shrink-0" aria-hidden="true" />
      <span aria-current="page" className="text-foreground min-w-0 truncate">
        {data.title}
      </span>
      <span className="text-fg-meta ml-auto shrink-0 pl-3 font-mono text-xs max-sm:hidden">
        {data.slug}
      </span>
    </nav>
  );
  const statement = (
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
      className="min-h-0 flex-1"
    >
      <div className="border-border-soft border-b-hairline shrink-0">
        <TabsList aria-label="题目内容">
          <TabsTrigger value="statement">题目描述</TabsTrigger>
          <TabsTrigger value="submissions">提交记录</TabsTrigger>
        </TabsList>
      </div>
      <TabsContent value="statement" className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
        {unavailable ? (
          <p role="alert" className="text-fg-muted p-4">
            题目状态暂时无法确认，请刷新页面。历史代码仍可复制。
          </p>
        ) : (
          <ProblemStatement data={data} />
        )}
      </TabsContent>
      <TabsContent
        value="submissions"
        className="min-h-0 flex-1 overflow-y-auto overscroll-contain"
      >
        <SubmissionHistoryPanel
          problem={data}
          canLoad={!unavailable}
          onLoad={(value) => {
            if (!unavailable) historyEditor.current?.requestLoad(value);
          }}
        />
      </TabsContent>
    </Tabs>
  );
  const code = (
    <ProblemCode
      key={data.problemId}
      data={data}
      historyRef={historyEditor}
      unavailable={unavailable}
      wide={wide}
    />
  );

  // 宽屏：左边题目，右边代码与测试上下排，缝隙都能拖。做题时大部分时间在写和测，
  // 所以默认题目占 42%，代码与测试占得多些；拖过的比例记在本机。
  if (wide) {
    return (
      <WorkbenchPageTemplate
        variant="coding"
        title={data.title}
        titleVisible={false}
        statusBar={breadcrumb}
      >
        <SplitPane
          orientation="horizontal"
          label="调整题目与代码的宽度"
          storageKey="cherry-oj.problem-split.columns"
          defaultRatio={0.42}
          className="h-full"
          first={<Pane label="题目">{statement}</Pane>}
          second={code}
        />
      </WorkbenchPageTemplate>
    );
  }

  // 窄屏：题目与代码切换着看，不分栏。
  return (
    <WorkbenchPageTemplate
      variant="coding"
      title={data.title}
      titleVisible={false}
      statusBar={breadcrumb}
    >
      <Tabs
        value={pane}
        onValueChange={(value) => setPane(String(value))}
        className="bg-panel h-full min-h-0"
      >
        <TabsList aria-label="答题区域">
          <TabsTrigger value="statement">
            <BookOpen aria-hidden="true" />
            题目
          </TabsTrigger>
          <TabsTrigger value="code">
            <Code2 aria-hidden="true" />
            代码
          </TabsTrigger>
        </TabsList>
        <TabsContent
          keepMounted
          value="statement"
          hidden={pane !== 'statement'}
          inert={pane !== 'statement'}
          className={cn('min-h-0 flex-1', pane === 'statement' ? 'flex flex-col' : 'hidden')}
        >
          {statement}
        </TabsContent>
        <TabsContent
          keepMounted
          value="code"
          hidden={pane !== 'code'}
          inert={pane !== 'code'}
          tabIndex={0}
          className={cn(
            'min-h-0 flex-1 overflow-y-auto overscroll-contain',
            pane === 'code' ? 'flex flex-col' : 'hidden',
          )}
        >
          {code}
        </TabsContent>
      </Tabs>
    </WorkbenchPageTemplate>
  );
}

// 题面按 linear.app 的 issue 详情排版：24px/600 标题，属性是一排小胶囊，区块标题 15px/600，
// 正文与管理端编辑器共用排版（所见即所得）。样例是并排的代码块，右上角复制。
function ProblemStatement({ data }: { data: ProblemDetail }) {
  return (
    <article className="text-fg-2 @container min-w-0 px-4 pt-4 pb-12 sm:px-8 sm:pt-6">
      {/* 页面的 h1 已由模板给出（读屏），这里是它的可见形式。 */}
      <p aria-hidden="true" className="text-foreground font-heading text-2xl leading-[38px]">
        {data.title}
      </p>
      <ul aria-label="题目属性" className="mt-3 flex flex-wrap items-center gap-1.5">
        <li className={propertyPillClasses}>
          <DifficultyIcon difficulty={data.difficulty} />
          {difficultyLabel(data.difficulty)}
        </li>
        <li className={propertyPillClasses}>{codeModeLabels[data.codeMode]}</li>
        {data.tags.map((tag) => (
          <li key={tag} className={propertyPillClasses}>
            {tag}
          </li>
        ))}
      </ul>
      <SafeMarkdown className="mt-6" value={data.statementMarkdown} />
      <StatementSection title="输入说明">
        <SafeMarkdown value={data.inputDescriptionMarkdown} />
      </StatementSection>
      <StatementSection title="输出说明">
        <SafeMarkdown value={data.outputDescriptionMarkdown} />
      </StatementSection>
      {data.constraintsMarkdown ? (
        <StatementSection title="数据范围">
          <SafeMarkdown value={data.constraintsMarkdown} />
        </StatementSection>
      ) : null}
      <StatementSection title="样例">
        <div className="grid gap-6">
          {data.samples.map((sample) => (
            <section
              key={sample.ordinal}
              aria-label={`样例 ${sample.ordinal}`}
              className="grid min-w-0 gap-2"
            >
              <span className="text-fg-meta text-sm">样例 {sample.ordinal}</span>
              <div className="grid min-w-0 gap-3 @lg:grid-cols-2">
                <SampleBlock
                  caption="输入"
                  value={sample.input}
                  copyLabel={`复制样例 ${sample.ordinal} 输入`}
                />
                <SampleBlock
                  caption="输出"
                  value={sample.output}
                  copyLabel={`复制样例 ${sample.ordinal} 输出`}
                />
              </div>
              {sample.explanationMarkdown ? (
                <SafeMarkdown value={sample.explanationMarkdown} />
              ) : null}
            </section>
          ))}
        </div>
      </StatementSection>
      {data.hintMarkdown ? (
        <StatementSection title="提示">
          <SafeMarkdown value={data.hintMarkdown} />
        </StatementSection>
      ) : null}
    </article>
  );
}

const propertyPillClasses =
  'bg-surface shadow-ring text-fg-2 inline-flex h-6 items-center gap-1.5 rounded-full px-2 text-xs';

function StatementSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mt-8">
      <Heading level={2} className="text-foreground font-heading text-15 mb-3">
        {title}
      </Heading>
      {children}
    </section>
  );
}

// 只读的样例代码块，与管理端样例编辑块同一外观（比面板亮一档的底、0.5px 边、6px 圆角、12px 等宽）。
function SampleBlock({
  caption,
  value,
  copyLabel,
}: {
  caption: string;
  value: string;
  copyLabel: string;
}) {
  return (
    <div className="bg-surface border-border border-hairline min-w-0 rounded-md">
      <div className="flex h-8 items-center justify-between gap-2 pr-1.5 pl-4">
        <span className="text-fg-meta text-xs">{caption}</span>
        <CopyIconButton value={value} label={copyLabel} />
      </div>
      <pre className="text-fg-2 overflow-x-auto px-4 pb-3 font-mono text-xs leading-[20px]">
        <code>{value || ' '}</code>
      </pre>
    </div>
  );
}

function ProblemCode({
  data,
  historyRef,
  unavailable,
  wide,
}: {
  data: ProblemDetail;
  historyRef: Ref<HistoryEditorHandle>;
  unavailable: boolean;
  wide: boolean;
}) {
  const session = useQuery({ ...sessionQueryOptions(), refetchInterval: 15_000 });
  const language =
    data.allowedLanguages.find((item) => item.id === 'cpp') ?? data.allowedLanguages[0];
  // ACM 与核心代码都可以作答：核心代码的前缀与驱动代码在服务端合并，编辑器里只有用户自己的代码。
  const supported = language?.id === 'cpp';
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
              ? '当前页面暂不支持此题目的语言。'
              : null;
  const header = (
    <>
      <span className="text-fg-2 font-body inline-flex items-center gap-1.5 text-sm">
        <Code2 className="size-3.5" aria-hidden="true" />
        代码
      </span>
      <span className="text-fg-disabled">·</span>
      <span>{language?.displayName ?? '无可用语言'}</span>
      <span>{codeModeLabels[data.codeMode]}</span>
    </>
  );
  const noticeBar = notice ? (
    <div
      className="border-border-soft text-fg-2 border-b-hairline flex shrink-0 flex-wrap items-center gap-2 px-3 py-2 text-xs"
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
  ) : null;

  if (!(editableIdentity && language)) {
    const readOnlyCode = (
      <>
        <PaneHeader start={header} />
        {noticeBar}
        <SourceEditor value={language?.starterCode ?? ''} onChange={() => undefined} readOnly />
      </>
    );
    return wide ? <Pane label="代码">{readOnlyCode}</Pane> : readOnlyCode;
  }

  return (
    <DraftEditor
      key={`${user.id}:${data.problemId}:${language.id}`}
      userId={user.id}
      problemId={data.problemId}
      languageId={language.id}
      starterCode={language.starterCode}
      readOnly={session.isError || unavailable}
      historyRef={historyRef}
      toolbarStart={header}
      renderSubmission={(source) => (
        <CustomRunWorkbench
          userId={user.id}
          problem={data}
          source={source}
          disabled={session.isError || unavailable}
          wide={wide}
        />
      )}
      {...(wide
        ? {
            layout: (editor: ReactNode, submission: ReactNode) => (
              <SplitPane
                orientation="vertical"
                label="调整代码与测试区的高度"
                storageKey="cherry-oj.problem-split.rows"
                defaultRatio={0.62}
                min={0.3}
                max={0.85}
                className="min-h-0 flex-1"
                first={
                  <Pane label="代码">
                    {noticeBar}
                    {editor}
                  </Pane>
                }
                second={<Pane label="测试">{submission}</Pane>}
              />
            ),
          }
        : {
            layout: (editor: ReactNode, submission: ReactNode) => (
              <>
                {noticeBar}
                {editor}
                {submission}
              </>
            ),
          })}
    />
  );
}
