import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';

import { AsyncState } from '@/components/ui/async-state';
import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Panel } from '@/components/ui/card';
import { Cluster, Container, Section, Stack } from '@/components/ui/layout';
import { Heading, Text } from '@/components/ui/typography';
import { ApiError } from '@/lib/api/api-client';

import { problemQuery } from '../api/problems-api';
import { SafeMarkdown } from './safe-markdown';

export function ProblemDetailPage({ slug }: { slug: string }) {
  const problem = useQuery(problemQuery(slug));
  if (problem.isPending)
    return (
      <Container>
        <Section className="py-10">
          <AsyncState
            variant="loading"
            size="page"
            title="正在加载题目…"
            progressLabel="正在加载题目…"
          >
            {null}
          </AsyncState>
        </Section>
      </Container>
    );
  if (problem.isError) {
    const notFound =
      problem.error instanceof ApiError && problem.error.code === 'PROBLEM_NOT_FOUND';
    return (
      <Container>
        <Section className="py-10">
          <AsyncState
            variant={notFound ? 'empty' : 'error'}
            size="page"
            title={notFound ? '题目不存在或尚未公开' : '题目暂时无法加载'}
            action={
              <Link
                to="/problems"
                search={{ sort: 'UPDATED_DESC', size: 20 }}
                className={buttonVariants({ variant: 'secondary' })}
              >
                返回题库
              </Link>
            }
          >
            {notFound ? (
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
  const data = problem.data;
  return (
    <Container>
      <Section className="py-10">
        <Stack gap={3}>
          <Text size="sm" tone="muted">
            {data.slug} · v{data.versionNo}
          </Text>
          <Heading level={1} size="2xl">
            {data.title}
          </Heading>
          <Cluster gap={2}>
            <Badge>{data.difficulty}</Badge>
            <Badge>{data.codeMode}</Badge>
            {data.tags.map((tag) => (
              <Badge key={tag} variant="info">
                {tag}
              </Badge>
            ))}
          </Cluster>
        </Stack>
        <div className="mt-8 grid min-w-0 gap-8 lg:grid-cols-[minmax(0,1fr)_18rem]">
          <article className="min-w-0 space-y-8">
            <SafeMarkdown value={data.statementMarkdown} />
            <section>
              <Heading level={2} size="lg">
                输入说明
              </Heading>
              <SafeMarkdown className="mt-3" value={data.inputDescriptionMarkdown} />
            </section>
            <section>
              <Heading level={2} size="lg">
                输出说明
              </Heading>
              <SafeMarkdown className="mt-3" value={data.outputDescriptionMarkdown} />
            </section>
            {data.constraintsMarkdown ? (
              <section>
                <Heading level={2} size="lg">
                  数据范围
                </Heading>
                <SafeMarkdown className="mt-3" value={data.constraintsMarkdown} />
              </section>
            ) : null}
            <section>
              <Heading level={2} size="lg">
                样例
              </Heading>
              <div className="mt-3 grid gap-4">
                {data.samples.map((sample) => (
                  <Panel key={sample.ordinal}>
                    <Text size="sm" tone="muted">
                      样例 {sample.ordinal}
                    </Text>
                    <div className="mt-3 grid gap-3 sm:grid-cols-2">
                      <pre className="bg-surface-subtle overflow-x-auto rounded-md p-3 font-mono text-sm">
                        <code>{sample.input}</code>
                      </pre>
                      <pre className="bg-surface-subtle overflow-x-auto rounded-md p-3 font-mono text-sm">
                        <code>{sample.output}</code>
                      </pre>
                    </div>
                    {sample.explanationMarkdown ? (
                      <SafeMarkdown className="mt-3" value={sample.explanationMarkdown} />
                    ) : null}
                  </Panel>
                ))}
              </div>
            </section>
          </article>
          <aside>
            <Panel className="sticky top-20">
              <Heading level={2} size="lg">
                可用语言
              </Heading>
              <ul className="mt-3 space-y-2">
                {data.allowedLanguages.map((language) => (
                  <li key={language.id}>
                    <Badge variant="success">{language.displayName}</Badge>
                  </li>
                ))}
              </ul>
              <Link
                to="/login"
                search={{ returnTo: `/problems/${data.slug}` }}
                className={buttonVariants({ className: 'mt-5 w-full' })}
              >
                登录后开始答题
              </Link>
            </Panel>
          </aside>
        </div>
      </Section>
    </Container>
  );
}
