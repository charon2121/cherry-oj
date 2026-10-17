import { CheckCircle2, ChevronRight, FileArchive, RotateCcw, XCircle } from 'lucide-react';
import { type DragEvent, type ReactNode, useRef, useState } from 'react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { TextEditor } from '@/components/ui/text-editor';
import { Text } from '@/components/ui/typography';
import type { AdminProblem, PublishCheck } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';
import { cn } from '@/lib/utils';

// 工作台里不属于"写题面"的动作——替换测试数据、校准、公开——各自是一个从属性栏打开的对话框
// （linear.app 新建 issue 对话框：宽 750、21px 圆角、标题 18px/600）。正文页只负责写。

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ApiError)) return error instanceof Error ? error.message : fallback;
  const request = error.requestId ? `（请求 ID：${error.requestId}）` : '';
  return `${error.problem?.detail ?? error.message ?? fallback}${request}`;
}

export function formatBytes(value: number | null): string {
  if (value === null) return '大小未知';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`;
  return `${(value / 1024 / 1024).toFixed(1)} MiB`;
}

function BlockedReason({ children }: { children: ReactNode }) {
  return (
    <Text size="sm" tone="meta" role="status">
      {children}
    </Text>
  );
}

export function TestDataDialog({
  open,
  onOpenChange,
  testData,
  blockedReason,
  uploading,
  uploadState,
  onUpload,
  onCancelUpload,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  testData: AdminProblem['testData'];
  blockedReason: string | undefined;
  uploading: boolean;
  uploadState: string | undefined;
  onUpload: (file: File) => void;
  onCancelUpload: () => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const disabled = blockedReason !== undefined || uploading;

  const onDrop = (event: DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    setDragging(false);
    const file = event.dataTransfer.files[0];
    if (file && !disabled) onUpload(file);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="md">
        <DialogHeader>
          <DialogTitle>测试数据</DialogTitle>
          <DialogDescription>
            一道题只有一份测试数据：上传即替换，替换后旧的校准过期。ZIP 中 .in/.out
            必须成对、文件名需安全。
          </DialogDescription>
        </DialogHeader>

        {testData ? (
          <div className="min-w-0 text-sm">
            <span className="text-fg-2 font-body">当前测试数据</span>
            <span className="text-fg-meta">
              {' '}
              · {testData.testcaseCount} 组 · {formatBytes(testData.totalBytes)}
            </span>
            <details className="text-fg-meta mt-1 text-xs">
              <summary className="cursor-pointer">摘要</summary>
              <span className="font-mono wrap-anywhere">{testData.digest}</span>
            </details>
          </div>
        ) : (
          <Text size="sm" tone="meta">
            还没有测试数据。
          </Text>
        )}

        <input
          ref={inputRef}
          type="file"
          aria-label="测试数据 ZIP"
          accept=".zip,application/zip"
          className="sr-only"
          disabled={disabled}
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) onUpload(file);
            event.currentTarget.value = '';
          }}
        />
        <button
          type="button"
          disabled={disabled}
          onClick={() => inputRef.current?.click()}
          onDragOver={(event) => {
            event.preventDefault();
            if (!disabled) setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          className={cn(
            'border-border-strong text-fg-meta hover:text-fg-2 hover:bg-surface-hover focus-visible:outline-ring duration-fast disabled:text-fg-disabled grid min-h-28 place-items-center gap-1 rounded-lg border border-dashed px-4 py-6 text-sm transition-colors outline-none focus-visible:outline-1 disabled:cursor-not-allowed disabled:hover:bg-transparent motion-reduce:transition-none',
            dragging && 'bg-surface-hover text-fg-2',
          )}
        >
          <FileArchive className="size-5" aria-hidden="true" />
          <span>{testData ? '选择或拖入新的 ZIP 替换' : '选择或拖入 ZIP 上传'}</span>
        </button>

        {blockedReason ? <BlockedReason>{blockedReason}</BlockedReason> : null}
        {uploading ? <progress className="w-full" aria-label="测试数据正在上传并检查" /> : null}
        {uploadState ? (
          <Text size="sm" role="status" aria-live="polite">
            {uploadState}
          </Text>
        ) : null}

        <DialogFooter>
          {uploading ? (
            <Button variant="secondary" onClick={onCancelUpload}>
              取消上传
            </Button>
          ) : null}
          <DialogClose render={<Button variant="secondary" />}>完成</DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export type CalibrationLimits = { cpuSeconds: string; memoryMib: string; clockSeconds: string };

function LimitInput({
  label,
  unit,
  value,
  disabled,
  placeholder,
  onChange,
}: {
  label: string;
  unit: string;
  value: string;
  disabled: boolean;
  placeholder?: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="bg-surface border-border-soft border-hairline focus-within:outline-ring grid min-w-0 gap-0.5 rounded-lg px-3 py-2 focus-within:outline-1 focus-within:-outline-offset-1">
      <span className="text-fg-meta text-xs">{label}</span>
      <span className="flex items-baseline gap-1">
        <input
          inputMode="decimal"
          value={value}
          disabled={disabled}
          placeholder={placeholder}
          onChange={(event) => onChange(event.target.value)}
          className="text-foreground placeholder:text-fg-disabled w-full min-w-0 bg-transparent text-sm outline-none"
        />
        <span className="text-fg-meta text-xs">{unit}</span>
      </span>
    </label>
  );
}

export function CalibrationDialog({
  open,
  onOpenChange,
  limits,
  onLimitsChange,
  referenceLabel,
  referenceSource,
  onReferenceSourceChange,
  editable,
  blockedReason,
  running,
  resultMessage,
  errorMessage,
  onRun,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  limits: CalibrationLimits;
  onLimitsChange: (limits: CalibrationLimits) => void;
  /** ACM 为"参考程序"；CORE 为"参考核心代码"，服务端会加上前缀并配合驱动代码运行。 */
  referenceLabel: string;
  referenceSource: string;
  onReferenceSourceChange: (value: string) => void;
  editable: boolean;
  blockedReason: string | undefined;
  running: boolean;
  resultMessage: string | undefined;
  errorMessage: string | undefined;
  onRun: () => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="lg">
        <DialogHeader>
          <DialogTitle>运行{referenceLabel}校准</DialogTitle>
          <DialogDescription>
            用{referenceLabel}跑一遍当前测试数据，确认限制可用。{referenceLabel}
            只在当前页面内存里，校准成功或离开工作台后清空。
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-2 sm:grid-cols-3">
          <LimitInput
            label="CPU 时间"
            unit="秒"
            value={limits.cpuSeconds}
            disabled={!editable}
            onChange={(cpuSeconds) => onLimitsChange({ ...limits, cpuSeconds })}
          />
          <LimitInput
            label="内存"
            unit="MiB"
            value={limits.memoryMib}
            disabled={!editable}
            onChange={(memoryMib) => onLimitsChange({ ...limits, memoryMib })}
          />
          <LimitInput
            label="墙钟时间"
            unit="秒"
            placeholder="可选"
            value={limits.clockSeconds}
            disabled={!editable}
            onChange={(clockSeconds) => onLimitsChange({ ...limits, clockSeconds })}
          />
        </div>
        <TextEditor
          language="cpp"
          size="compact"
          value={referenceSource}
          readOnly={!editable}
          placeholder={`粘贴 C++ ${referenceLabel}`}
          aria-label={`C++ ${referenceLabel}编辑器`}
          onChange={onReferenceSourceChange}
        />
        {blockedReason ? <BlockedReason>{blockedReason}</BlockedReason> : null}
        {resultMessage ? (
          <Text size="sm" role="status">
            {resultMessage}
          </Text>
        ) : null}
        {errorMessage ? (
          <Text className="text-danger" size="sm" role="alert">
            {errorMessage}
          </Text>
        ) : null}
        <DialogFooter>
          <DialogClose render={<Button variant="secondary" />}>关闭</DialogClose>
          <Button
            loading={running}
            disabled={blockedReason !== undefined || !referenceSource.trim()}
            onClick={onRun}
          >
            运行{referenceLabel}校准
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function PublishDialog({
  open,
  onOpenChange,
  check,
  checkPending,
  checkFetching,
  checkError,
  onRecheck,
  isPublic,
  blockedReason,
  acting,
  actionError,
  onPublish,
  onUnpublish,
  onResolve,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  check: PublishCheck | undefined;
  checkPending: boolean;
  checkFetching: boolean;
  checkError: string | undefined;
  onRecheck: () => void;
  isPublic: boolean;
  blockedReason: string | undefined;
  acting: boolean;
  actionError: string | undefined;
  onPublish: () => void;
  onUnpublish: () => void;
  onResolve: (code: string) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="md">
        <DialogHeader>
          <DialogTitle>{isPublic ? '公开设置' : '检查与公开'}</DialogTitle>
          <DialogDescription>
            {isPublic
              ? '题目已公开。取消公开后变回私有，已有提交记录保留。'
              : '公开后学生立即可见；之后的修改也会立即生效。服务端检查是最终公开依据。'}
          </DialogDescription>
        </DialogHeader>

        <div className="grid">
          {checkPending ? (
            <AsyncState
              variant="loading"
              size="inline"
              title="正在运行公开检查…"
              progressLabel="正在运行公开检查…"
            >
              {null}
            </AsyncState>
          ) : null}
          {check?.checks.map((item) => (
            <div
              key={item.code}
              className="flex min-h-9 min-w-0 items-center gap-3 rounded-lg text-sm"
            >
              {item.passed ? (
                <CheckCircle2 className="text-success size-4 shrink-0" aria-hidden="true" />
              ) : (
                <XCircle className="text-danger size-4 shrink-0" aria-hidden="true" />
              )}
              <span className={cn('min-w-0 flex-1', item.passed ? 'text-fg-meta' : 'text-fg-2')}>
                {item.message}
              </span>
              {!item.passed ? (
                <Button size="sm" variant="ghost" onClick={() => onResolve(item.code)}>
                  去处理
                  <ChevronRight aria-hidden="true" />
                </Button>
              ) : null}
            </div>
          ))}
        </div>
        {checkError ? (
          <Text className="text-danger" size="sm" role="alert">
            {checkError}
          </Text>
        ) : null}
        {blockedReason ? <BlockedReason>{blockedReason}</BlockedReason> : null}
        {actionError ? (
          <Text className="text-danger" size="sm" role="alert">
            {actionError}
          </Text>
        ) : null}

        <DialogFooter className="sm:justify-between">
          <Button variant="ghost" size="sm" loading={checkFetching} onClick={onRecheck}>
            <RotateCcw aria-hidden="true" />
            重新检查
          </Button>
          <div className="flex flex-col gap-2 sm:flex-row">
            <DialogClose render={<Button variant="secondary" disabled={acting} />}>
              取消
            </DialogClose>
            {isPublic ? (
              <Button
                variant="secondary"
                loading={acting}
                disabled={blockedReason !== undefined}
                onClick={onUnpublish}
              >
                取消公开
              </Button>
            ) : (
              <Button
                loading={acting}
                disabled={blockedReason !== undefined || !check?.ready}
                onClick={onPublish}
              >
                公开题目
              </Button>
            )}
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function ConfirmationDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  onConfirm,
  loading = false,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  confirmLabel: string;
  onConfirm: () => Promise<void>;
  loading?: boolean;
}) {
  const [error, setError] = useState<string>();
  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (loading) return;
        onOpenChange(nextOpen);
        if (!nextOpen) setError(undefined);
      }}
    >
      <DialogContent size="sm">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {error ? (
          <Text className="text-danger" size="sm" role="alert">
            {error}
          </Text>
        ) : null}
        <DialogFooter>
          <DialogClose render={<Button variant="secondary" disabled={loading} />}>取消</DialogClose>
          <Button
            variant="danger"
            loading={loading}
            onClick={() => {
              void (async () => {
                setError(undefined);
                try {
                  await onConfirm();
                  onOpenChange(false);
                } catch (caught) {
                  setError(apiErrorMessage(caught, '操作失败，请刷新状态后重试。'));
                }
              })();
            }}
          >
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
