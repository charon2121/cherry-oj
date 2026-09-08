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
import { SelectField } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { TextEditor } from '@/components/ui/text-editor';
import { authKeys } from '@/features/auth/api/session-query';
import { SourceCopyButton } from '@/features/problems/components/source-copy-button';
import { SubmissionPanel } from '@/features/submissions/submission-panel';
import { loadRecovery, recoveryKey } from '@/features/submissions/submission-recovery';
import type {
  AuthSessionData,
  CustomRunData,
  CustomRunRequest,
  ProblemDetail,
} from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';

import { createCustomRun } from './custom-run-api';

const labels: Record<CustomRunData['status'], string> = {
  COMPLETED: '运行完成（未校验答案）',
  COMPILE_ERROR: '编译失败',
  RUNTIME_ERROR: '程序运行错误',
  TIME_LIMIT_EXCEEDED: '超过时间限制',
  MEMORY_LIMIT_EXCEEDED: '超过内存限制',
  OUTPUT_LIMIT_EXCEEDED: '超过输出限制',
};
export function CustomRunWorkbench({
  userId,
  problem,
  source,
  disabled,
}: {
  userId: string;
  problem: ProblemDetail;
  source: string;
  disabled: boolean;
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
  const [input, setInput] = useState('');
  const [replaceInput, setReplaceInput] = useState<string | null>(null);
  const [attempt, setAttempt] = useState<{ body: CustomRunRequest; started: string } | null>(null);
  const [result, setResult] = useState<CustomRunData | null>(null);
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
      setResult(null);
      setError('');
      setReplaceInput(null);
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
  const inputLarge = new TextEncoder().encode(input).length > 65536;
  const invalidSource = !source.trim() || new TextEncoder().encode(source).length > 262144;
  const sameUser = () => {
    const session = client.getQueryData<AuthSessionData>(authKeys.session());
    return (
      session?.authenticated &&
      session.user.id === userId &&
      session.user.status === 'ACTIVE' &&
      !session.user.passwordChangeRequired
    );
  };
  async function run() {
    if (active.current || disabled || inputLarge || invalidSource || !sameUser()) return;
    const controller = new AbortController();
    active.current = controller;
    const current = ++sequence.current;
    const body: CustomRunRequest = {
      problemId: problem.problemId,
      expectedProblemVersionId: problem.problemVersionId,
      languageId: 'cpp',
      source,
      inputText: input,
    };
    setAttempt({ body, started: new Date().toLocaleTimeString() });
    setResult(null);
    setError('');
    setBusy(true);
    setTab('run');
    try {
      const value = await createCustomRun(
        body,
        userId,
        AbortSignal.any([controller.signal, AbortSignal.timeout(60000)]),
      );
      if (current === sequence.current && sameUser()) setResult(value);
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
  function choose(value: string) {
    if (disabled) return;
    if (input && input !== value) setReplaceInput(value);
    else setInput(value);
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
          disabled={disabled || busy || inputLarge || invalidSource}
          onClick={() => void run()}
          aria-describedby="custom-run-help"
        >
          <Play aria-hidden="true" />
          {busy && !disabled ? '正在运行…' : '运行'}
        </Button>
      }
      renderResults={(submission) => (
        <>
          <p id="custom-run-help" className="text-fg-muted text-xs">
            {disabled
              ? '当前题目或登录状态尚不可运行。'
              : invalidSource
                ? '请输入非空 C++ 代码，最多 256 KiB。'
                : inputLarge
                  ? '自定义输入超过 64 KiB，请缩减后运行。'
                  : '自测只运行当前代码，不校验答案，也不计入提交记录。'}
          </p>
          <Tabs value={tab} onValueChange={(value) => setTab(String(value))}>
            <TabsList aria-label="测试与提交">
              <TabsTrigger value="input">自定义输入</TabsTrigger>
              <TabsTrigger value="run">运行结果</TabsTrigger>
              <TabsTrigger value="submission">提交结果</TabsTrigger>
            </TabsList>
            <TabsContent value="input" className="space-y-3 pt-3">
              <div className="flex flex-wrap items-center gap-2">
                {problem.samples.length > 0 ? (
                  <SelectField
                    label="使用样例"
                    labelPlacement="hidden"
                    className="relative"
                    disabled={disabled}
                    value="none"
                    items={[
                      { value: 'none', label: '使用样例' },
                      ...problem.samples.map((s, index) => ({
                        value: String(index),
                        label: `样例 ${s.ordinal}`,
                      })),
                    ]}
                    onValueChange={(value) => {
                      if (value !== 'none') {
                        const sample = problem.samples[Number(value)];
                        if (sample) choose(sample.input);
                      }
                    }}
                  />
                ) : null}
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={disabled || !input}
                  onClick={() => choose('')}
                >
                  清空输入
                </Button>
              </div>
              <TextEditor
                value={input}
                onChange={setInput}
                readOnly={disabled}
                language="plain"
                size="compact"
                aria-label="自定义输入数据"
              />
              <p className="text-fg-muted text-xs">
                输入程序需要的数据；不需要输入时可以留空。输入刷新后清除。
              </p>
            </TabsContent>
            <TabsContent value="run" className="space-y-3 pt-3">
              {disabled ? (
                <p className="text-fg-muted">登录或题目状态暂时无法确认，运行结果已隐藏。</p>
              ) : (
                <>
                  {attempt ? (
                    <p className="text-fg-meta text-xs">
                      {attempt.started} · v{problem.versionNo} · C++
                    </p>
                  ) : null}
                  {busy ? <p role="status">正在运行…</p> : null}
                  {error ? (
                    <p role="alert" className="text-fg-2">
                      {error}
                    </p>
                  ) : null}
                  {result ? (
                    <RunResult value={result} />
                  ) : !busy && !error ? (
                    <p className="text-fg-muted">填写输入后点击运行，结果会显示在这里。</p>
                  ) : null}
                  {attempt ? (
                    <p className="text-fg-muted text-xs">
                      {source !== attempt.body.source || input !== attempt.body.inputText
                        ? '代码或输入已修改，这些修改尚未运行。'
                        : '结果对应本次运行时的代码和输入。'}
                    </p>
                  ) : null}
                </>
              )}
            </TabsContent>
            <TabsContent value="submission" className="pt-3">
              {submission}
            </TabsContent>
          </Tabs>
          <Dialog
            open={replaceInput !== null && !disabled}
            onOpenChange={(open) => {
              if (!open) setReplaceInput(null);
            }}
          >
            <DialogContent>
              <DialogHeader>
                <DialogTitle>替换自定义输入？</DialogTitle>
                <DialogDescription>
                  当前输入将被替换。需要保留时，请先取消并复制备份。
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="secondary" onClick={() => setReplaceInput(null)}>
                  取消
                </Button>
                <Button
                  onClick={() => {
                    if (replaceInput !== null && !disabled) setInput(replaceInput);
                    setReplaceInput(null);
                  }}
                >
                  确认替换
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </>
      )}
    />
  );
}
function RunResult({ value }: { value: CustomRunData }) {
  return (
    <section aria-label="自定义运行结果" className="space-y-3">
      <p className="text-foreground">{labels[value.status]}</p>
      {value.cpuNs !== undefined && value.memoryBytes !== undefined ? (
        <p className="text-fg-2 font-mono text-xs">
          CPU {value.cpuNs / 1000000} ms · 内存 {value.memoryBytes / 1024} KiB
        </p>
      ) : null}
      <p className="text-fg-meta font-mono text-xs">
        限制：CPU {value.effectiveLimits.cpuNs / 1000000} ms · 墙钟{' '}
        {value.effectiveLimits.clockNs / 1000000} ms · 内存{' '}
        {value.effectiveLimits.memoryBytes / 1048576} MiB
      </p>
      {value.compileDiagnostic !== undefined ? (
        <OutputView label="编译诊断" text={value.compileDiagnostic} />
      ) : null}
      {value.stdout ? (
        <OutputView
          label="标准输出"
          text={value.stdout.text}
          bytes={value.stdout.capturedBytes}
          truncated={value.stdout.truncated}
        />
      ) : null}
      {value.stderr ? (
        <OutputView
          label="错误输出"
          text={value.stderr.text}
          bytes={value.stderr.capturedBytes}
          truncated={value.stderr.truncated}
        />
      ) : null}
      <p className="text-fg-muted text-xs">
        纯文本预览，非二进制无损输出。自测耗时不代表正式提交成绩。
      </p>
    </section>
  );
}
function OutputView({
  label,
  text,
  bytes,
  truncated,
}: {
  label: string;
  text: string;
  bytes?: number;
  truncated?: boolean;
}) {
  return (
    <section aria-label={label} className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-fg-2 text-sm">{label}</span>
        {bytes !== undefined ? (
          <span className="text-fg-meta font-mono text-xs">
            已捕获 {bytes} bytes{truncated ? ' · 展示已截断' : ''}
          </span>
        ) : null}
        <SourceCopyButton value={text} label={`复制${label}`} />
      </div>
      {text ? (
        <TextEditor
          value={text}
          onChange={() => undefined}
          readOnly
          language="plain"
          size="compact"
          aria-label={label}
        />
      ) : (
        <p className="text-fg-muted text-xs">无{label}</p>
      )}
    </section>
  );
}
