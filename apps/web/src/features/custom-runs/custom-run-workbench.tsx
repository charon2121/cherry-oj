import { useQueryClient } from '@tanstack/react-query';
import { useSearch } from '@tanstack/react-router';
import { Play } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Pill } from '@/components/ui/pill';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { TextEditor } from '@/components/ui/text-editor';
import { authKeys } from '@/features/auth/api/session-query';
import { CopyIconButton } from '@/features/problems/components/source-copy-button';
import { labels, SubmissionPanel } from '@/features/submissions/submission-panel';
import { loadRecovery, recoveryKey } from '@/features/submissions/submission-recovery';
import type { AuthSessionData, CustomRunData, ProblemDetail } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';
import { modKey, useModEnter } from '@/lib/use-mod-enter';
import { cn } from '@/lib/utils';

import { createCustomRun } from './custom-run-api';
import { type CaseVerdict, caseVerdict } from './sample-check';

// 判定名称与正式提交同一套；只有自测才有的两种结论另起名字。
const caseLabels: Record<CaseVerdict, string> = {
  ...labels,
  RAN: '运行完成',
  UNCHECKED: '运行完成（输出过长，未比对）',
};
type RunCase = { key: string; name: string; input: string; expected?: string };
type Outcome = { value: CustomRunData; verdict: CaseVerdict };
type Attempt = { source: string; custom: string; cases: RunCase[]; started: string };
const CUSTOM = 'custom';

function failed(verdict: CaseVerdict) {
  return verdict !== 'AC' && verdict !== 'RAN' && verdict !== 'UNCHECKED';
}

export function CustomRunWorkbench({
  userId,
  problem,
  source,
  disabled,
  wide = false,
}: {
  userId: string;
  problem: ProblemDetail;
  source: string;
  disabled: boolean;
  /** 宽屏做题页：结果区是一张卡片，正文在卡片里滚动。 */
  wide?: boolean;
}) {
  const client = useQueryClient();
  const search = useSearch({ from: '/_site/problems/$slug' });
  const [tab, setTab] = useState(() => {
    try {
      return search.submissionId || loadRecovery(recoveryKey(userId, problem.problemId))
        ? 'submission'
        : 'input';
    } catch {
      return 'submission';
    }
  });
  const samples: RunCase[] = problem.samples.map((sample) => ({
    key: `sample-${sample.ordinal}`,
    name: `样例 ${sample.ordinal}`,
    input: sample.input,
    expected: sample.output,
  }));
  const [custom, setCustom] = useState('');
  const [caseKey, setCaseKey] = useState(() => samples[0]?.key ?? CUSTOM);
  const [replaceCustom, setReplaceCustom] = useState<string | null>(null);
  const [attempt, setAttempt] = useState<Attempt | null>(null);
  const [outcomes, setOutcomes] = useState<Record<string, Outcome>>({});
  const [resultKey, setResultKey] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const active = useRef<AbortController | null>(null);
  const sequence = useRef(0);
  const [wasDisabled, setWasDisabled] = useState(disabled);
  if (wasDisabled !== disabled) {
    setWasDisabled(disabled);
    if (disabled) {
      setBusy(false);
      setAttempt(null);
      setOutcomes({});
      setError('');
      setReplaceCustom(null);
    }
  }
  useEffect(() => {
    const invalidate = () => {
      sequence.current += 1;
      active.current?.abort();
      active.current = null;
    };
    if (disabled) invalidate();
    return invalidate;
  }, [disabled]);
  // 宽屏时结果区是卡片里可滚动的正文。
  const body = wide ? 'min-h-0 flex-1 overflow-y-auto px-4 pb-4' : undefined;
  const inputLarge = new TextEncoder().encode(custom).length > 65536;
  const invalidSource = !source.trim() || new TextEncoder().encode(source).length > 262144;
  const sample = samples.find((item) => item.key === caseKey);
  const sameUser = () => {
    const session = client.getQueryData<AuthSessionData>(authKeys.session());
    return (
      session?.authenticated &&
      session.user.id === userId &&
      session.user.status === 'ACTIVE' &&
      !session.user.passwordChangeRequired
    );
  };
  // 运行一次跑全部样例；自定义输入写了内容才一起跑（题目没有样例时也跑它，哪怕是空输入）。
  function runCases(): RunCase[] {
    const own = { key: CUSTOM, name: '自定义', input: custom };
    return custom !== '' || samples.length === 0 ? [...samples, own] : samples;
  }
  async function run() {
    if (active.current || disabled || inputLarge || invalidSource || !sameUser()) return;
    const controller = new AbortController();
    active.current = controller;
    const current = ++sequence.current;
    const cases = runCases();
    setAttempt({ source, custom, cases, started: new Date().toLocaleTimeString() });
    setOutcomes({});
    setResultKey(cases[0]?.key ?? CUSTOM);
    setError('');
    setBusy(true);
    setTab('run');
    let firstFailure: string | undefined;
    try {
      // 服务端每个账号同时只受理一次运行，所以逐个发。
      for (const item of cases) {
        const value = await createCustomRun(
          { problemId: problem.problemId, languageId: 'cpp', source, inputText: item.input },
          userId,
          AbortSignal.any([controller.signal, AbortSignal.timeout(60000)]),
        );
        if (current !== sequence.current || !sameUser()) return;
        const verdict = caseVerdict(value, item.expected);
        setOutcomes((previous) => ({ ...previous, [item.key]: { value, verdict } }));
        if (failed(verdict)) firstFailure ??= item.key;
        // 编译错误与输入无关，后面的用例不用再跑。
        if (verdict === 'CE') break;
      }
      if (firstFailure) setResultKey(firstFailure);
    } catch (cause) {
      if (current === sequence.current && sameUser()) {
        setError(
          cause instanceof ApiError && ['network', 'timeout', 'aborted'].includes(cause.kind)
            ? '本次运行结果未能确认，请稍后手动重试。'
            : cause instanceof ApiError && cause.retryAfterNs !== undefined
              ? `${cause.message} 请在 ${Number((cause.retryAfterNs + 999999999n) / 1000000000n)} 秒后重试。`
              : cause instanceof Error
                ? cause.message
                : '运行失败，请稍后重试。',
        );
        if (
          cause instanceof ApiError &&
          ([401, 403].includes(cause.status ?? 0) || cause.code === 'SESSION_CHANGED')
        )
          void client.invalidateQueries({ queryKey: authKeys.session() });
      }
    } finally {
      if (current === sequence.current) {
        active.current = null;
        setBusy(false);
      }
    }
  }
  const runDisabled = disabled || busy || inputLarge || invalidSource;
  // ⌘/Ctrl + Enter 运行：在代码编辑器里也能直接按，不用把手离开键盘。
  useModEnter(false, () => {
    if (!runDisabled) void run();
  });
  // 写过的自定义输入被覆盖或清空前要确认。
  function writeCustom(value: string) {
    if (disabled) return;
    if (custom !== '' && custom !== value) setReplaceCustom(value);
    else {
      setCustom(value);
      setCaseKey(CUSTOM);
    }
  }
  return (
    <SubmissionPanel
      userId={userId}
      problem={problem}
      source={source}
      disabled={disabled}
      onSubmissionStart={() => setTab('submission')}
      runControl={
        <Button
          size="sm"
          variant="secondary"
          disabled={runDisabled}
          onClick={() => void run()}
          aria-describedby="custom-run-help"
          aria-keyshortcuts="Meta+Enter Control+Enter"
          title={`运行（${modKey} Enter）`}
        >
          <Play aria-hidden="true" />
          {busy && !disabled ? '正在运行…' : '运行'}
        </Button>
      }
      className={
        wide ? 'flex max-h-none min-h-0 flex-1 flex-col overflow-hidden border-0 p-0' : undefined
      }
      renderResults={(submission, actions) => (
        <>
          <Tabs
            value={tab}
            onValueChange={(value) => setTab(String(value))}
            className={wide ? 'min-h-0 flex-1' : undefined}
          >
            {/* 运行、提交放在测试卡片标题行右侧：紧挨着它们产生的结果，也离代码最近。 */}
            <div
              className={cn(
                'flex shrink-0 flex-wrap items-center gap-2',
                wide && 'border-border-soft border-b-hairline',
              )}
            >
              <TabsList aria-label="测试与提交" className="flex-1">
                <TabsTrigger value="input">测试用例</TabsTrigger>
                <TabsTrigger value="run">运行结果</TabsTrigger>
                <TabsTrigger value="submission">提交结果</TabsTrigger>
              </TabsList>
              <div className={cn('flex items-center gap-2', wide && 'pr-2')}>{actions}</div>
            </div>
            {/* 只在运行被挡住时说明原因；平常的说明只留给读屏，界面上不放。 */}
            <p
              id="custom-run-help"
              className={
                disabled || invalidSource || inputLarge
                  ? cn('text-fg-meta text-xs', wide && 'px-4 pt-2')
                  : 'sr-only'
              }
            >
              {disabled
                ? '当前题目或登录状态尚不可运行。'
                : invalidSource
                  ? '请输入非空 C++ 代码，最多 256 KiB。'
                  : inputLarge
                    ? '自定义输入超过 64 KiB，请缩减后运行。'
                    : '运行会用当前代码跑全部样例并比对预期输出，自定义输入有内容时一起运行；不计入提交记录。'}
            </p>
            <TabsContent value="input" className={cn('space-y-3 pt-3', body)}>
              <div className="flex flex-wrap items-center gap-1.5">
                {samples.map((item) => (
                  <Pill
                    key={item.key}
                    selected={caseKey === item.key}
                    onClick={() => setCaseKey(item.key)}
                  >
                    {item.name}
                  </Pill>
                ))}
                <Pill selected={caseKey === CUSTOM} onClick={() => setCaseKey(CUSTOM)}>
                  自定义
                </Pill>
              </div>
              {sample ? (
                <>
                  <TextEditor
                    value={sample.input}
                    onChange={() => undefined}
                    readOnly
                    language="plain"
                    size="auto"
                    aria-label={`${sample.name}输入`}
                  />
                  <Button
                    size="sm"
                    variant="ghost"
                    className="text-fg-meta"
                    disabled={disabled}
                    onClick={() => writeCustom(sample.input)}
                  >
                    复制到自定义
                  </Button>
                </>
              ) : (
                <>
                  <TextEditor
                    value={custom}
                    onChange={setCustom}
                    readOnly={disabled}
                    language="plain"
                    size="auto"
                    aria-label="自定义输入数据"
                  />
                  <Button
                    size="sm"
                    variant="ghost"
                    className="text-fg-meta"
                    disabled={disabled || !custom}
                    onClick={() => writeCustom('')}
                  >
                    清空
                  </Button>
                </>
              )}
            </TabsContent>
            <TabsContent value="run" className={cn('space-y-3 pt-3', body)}>
              {disabled ? (
                <p className="text-fg-muted">登录或题目状态暂时无法确认，运行结果已隐藏。</p>
              ) : attempt ? (
                <RunReport
                  attempt={attempt}
                  outcomes={outcomes}
                  busy={busy}
                  error={error}
                  selected={resultKey}
                  onSelect={setResultKey}
                  stale={source !== attempt.source || custom !== attempt.custom}
                />
              ) : (
                <p className="text-fg-muted">运行后结果会显示在这里。</p>
              )}
            </TabsContent>
            <TabsContent value="submission" className={cn('pt-3', body)}>
              {submission}
            </TabsContent>
          </Tabs>
          <Dialog
            open={replaceCustom !== null && !disabled}
            onOpenChange={(open) => {
              if (!open) setReplaceCustom(null);
            }}
          >
            <DialogContent>
              <DialogHeader>
                <DialogTitle>
                  {replaceCustom === '' ? '清空自定义输入？' : '替换自定义输入？'}
                </DialogTitle>
                <DialogDescription>
                  当前自定义输入将被{replaceCustom === '' ? '清空' : '替换'}
                  。需要保留时，请先取消并复制备份。
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="secondary" onClick={() => setReplaceCustom(null)}>
                  取消
                </Button>
                <Button
                  onClick={() => {
                    if (replaceCustom !== null && !disabled) {
                      setCustom(replaceCustom);
                      setCaseKey(CUSTOM);
                    }
                    setReplaceCustom(null);
                  }}
                >
                  {replaceCustom === '' ? '确认清空' : '确认替换'}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </>
      )}
    />
  );
}

// 一次运行的结果：先一句总结，再按用例切换查看输入、实际输出与预期输出。
function RunReport({
  attempt,
  outcomes,
  busy,
  error,
  selected,
  onSelect,
  stale,
}: {
  attempt: Attempt;
  outcomes: Record<string, Outcome>;
  busy: boolean;
  error: string;
  selected: string;
  onSelect: (key: string) => void;
  stale: boolean;
}) {
  const compile = Object.values(outcomes).find((outcome) => outcome.verdict === 'CE');
  const checked = attempt.cases.filter((item) => item.expected !== undefined);
  const passed = checked.filter((item) => outcomes[item.key]?.verdict === 'AC').length;
  const done = attempt.cases.filter((item) => outcomes[item.key]).length;
  const anyFailed = Object.values(outcomes).some((outcome) => failed(outcome.verdict));
  const current = attempt.cases.find((item) => item.key === selected) ?? attempt.cases[0];
  const firstVerdict = outcomes[attempt.cases[0]?.key ?? '']?.verdict;
  // 运行被中途打断（例如服务繁忙）时，总结不下结论，只显示错误。
  const summary = compile
    ? '编译错误'
    : busy
      ? `正在运行 ${Math.min(done + 1, attempt.cases.length)} / ${attempt.cases.length}…`
      : done < attempt.cases.length
        ? null
        : checked.length === 0
          ? caseLabels[firstVerdict ?? 'RAN']
          : passed === checked.length
            ? '样例全部通过'
            : `样例通过 ${passed} / ${checked.length}`;
  return (
    <section aria-label="自定义运行结果" className="space-y-3">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        {summary ? (
          <p
            role="status"
            className={cn(
              'font-heading',
              busy
                ? 'text-fg-2'
                : compile || anyFailed || passed < checked.length
                  ? 'text-danger'
                  : checked.length > 0
                    ? 'text-success'
                    : 'text-foreground',
            )}
          >
            {summary}
          </p>
        ) : null}
        <span className="text-fg-meta text-xs">{attempt.started} · C++</span>
      </div>
      {error ? (
        <p role="alert" className="text-fg-2">
          {error}
        </p>
      ) : null}
      {stale ? <p className="text-fg-meta text-xs">代码或输入已修改，这些修改尚未运行。</p> : null}
      {compile ? (
        <OutputView label="编译诊断" text={compile.value.compileDiagnostic ?? ''} />
      ) : (
        <>
          {attempt.cases.length > 1 ? (
            <div className="flex flex-wrap items-center gap-1.5">
              {attempt.cases.map((item) => {
                const outcome = outcomes[item.key];
                return (
                  <Pill
                    key={item.key}
                    selected={current?.key === item.key}
                    dot={outcome !== undefined}
                    dotClassName={
                      outcome === undefined
                        ? ''
                        : failed(outcome.verdict)
                          ? 'bg-danger'
                          : outcome.verdict === 'AC'
                            ? 'bg-success'
                            : 'bg-fg-meta'
                    }
                    onClick={() => onSelect(item.key)}
                  >
                    {item.name}
                    {outcome ? (
                      <span className="sr-only">（{caseLabels[outcome.verdict]}）</span>
                    ) : null}
                  </Pill>
                );
              })}
            </div>
          ) : null}
          {current ? <CaseResult item={current} outcome={outcomes[current.key]} /> : null}
        </>
      )}
    </section>
  );
}

function CaseResult({ item, outcome }: { item: RunCase; outcome: Outcome | undefined }) {
  if (!outcome) return <p className="text-fg-muted">{item.name}尚未运行。</p>;
  const { value, verdict } = outcome;
  return (
    <section aria-label={`${item.name}结果`} className="space-y-3">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        {/* 判定写代码加名称（design-system.md §5）；只有自测才有的两种结论没有代码。 */}
        <p className={failed(verdict) ? 'text-danger' : 'text-foreground'}>
          {verdict === 'RAN' || verdict === 'UNCHECKED'
            ? caseLabels[verdict]
            : `${verdict} · ${caseLabels[verdict]}`}
        </p>
        {/* 用量与限制同一行：限制只作为括号里的参照。 */}
        <p className="text-fg-meta font-mono text-xs">
          {value.cpuNs !== undefined && value.memoryBytes !== undefined ? (
            <span className="text-fg-2">
              CPU {value.cpuNs / 1000000} ms · 内存 {value.memoryBytes / 1024} KiB{' '}
            </span>
          ) : null}
          （限制 {value.effectiveLimits.cpuNs / 1000000} ms ·{' '}
          {value.effectiveLimits.memoryBytes / 1048576} MiB）
        </p>
      </div>
      <OutputView label="输入" text={item.input} />
      <OutputView
        label="实际输出"
        text={value.stdout?.text ?? ''}
        truncated={value.stdout?.truncated}
      />
      {item.expected !== undefined ? <OutputView label="预期输出" text={item.expected} /> : null}
      {value.stderr ? (
        <OutputView label="错误输出" text={value.stderr.text} truncated={value.stderr.truncated} />
      ) : null}
    </section>
  );
}

function OutputView({
  label,
  text,
  truncated,
}: {
  label: string;
  text: string;
  truncated?: boolean | undefined;
}) {
  return (
    <section aria-label={label} className="space-y-1.5">
      <div className="flex flex-wrap items-center gap-1">
        <span className="text-foreground font-heading text-sm">{label}</span>
        {truncated ? <span className="text-fg-meta text-xs">展示已截断</span> : null}
        <CopyIconButton value={text} label={`复制${label}`} />
      </div>
      {text ? (
        <TextEditor
          value={text}
          onChange={() => undefined}
          readOnly
          language="plain"
          size="auto"
          aria-label={label}
        />
      ) : (
        <p className="text-fg-meta text-xs">无{label}</p>
      )}
    </section>
  );
}
