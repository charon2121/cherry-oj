import { useBlocker } from '@tanstack/react-router';
import { Download, RotateCcw } from 'lucide-react';
import { type ReactNode, type Ref, useEffect, useImperativeHandle, useState } from 'react';

import { Button } from '@/components/ui/button';
import { CodeEditor } from '@/components/ui/code-editor';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { IconButton } from '@/components/ui/icon-button';
import { TextEditor } from '@/components/ui/text-editor';
import type { HistoryLoad } from '@/features/submissions/submission-history-panel';
import { cn } from '@/lib/utils';

import { useCodeDraft } from '../hooks/use-code-draft';
import { useWorkbenchMedia } from '../hooks/use-workbench-media';
import { CopyIconButton, SourceCopyButton } from './source-copy-button';

export function SourceEditor({
  value,
  onChange,
  readOnly = false,
}: {
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
}) {
  const touch = useWorkbenchMedia('(pointer: coarse)');
  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col [@media(max-height:36rem)]:h-64 [@media(max-height:36rem)]:flex-none">
      <div className="min-h-0 min-w-0 flex-1 overflow-hidden">
        {touch ? (
          <TextEditor
            value={value}
            onChange={onChange}
            readOnly={readOnly}
            language="cpp"
            size="fill"
            aria-label="C++ 代码编辑器"
            className="h-full rounded-none border-0"
          />
        ) : (
          <CodeEditor
            value={value}
            onChange={onChange}
            readOnly={readOnly}
            aria-label="C++ 代码编辑器"
            className="h-full"
          />
        )}
      </div>
    </div>
  );
}

/** 分栏顶部的一行（linear.app 详情页顶栏高 44px、0.5px 下边）：左边是标题或状态，右边是图标操作。 */
export function PaneHeader({ start, children }: { start?: ReactNode; children?: ReactNode }) {
  return (
    <div className="border-border-soft border-b-hairline flex h-11 shrink-0 items-center gap-1 pr-2 pl-4">
      <div className="text-fg-meta flex min-w-0 flex-1 items-center gap-2 text-xs">{start}</div>
      {children}
    </div>
  );
}

/** 做题页的一栏：平铺在面板上，栏与栏之间只有分隔线（linear.app 的分栏）。给了 label 就是一个区域。 */
export function Pane({
  children,
  className,
  label,
}: {
  children: ReactNode;
  className?: string;
  label?: string;
}) {
  return (
    <section
      aria-label={label}
      className={cn('bg-panel flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden', className)}
    >
      {children}
    </section>
  );
}

export type HistoryEditorHandle = { requestLoad: (value: HistoryLoad) => void };

type DraftEditorProps = {
  historyRef?: Ref<HistoryEditorHandle>;

  userId: string;
  problemId: string;
  languageId: string;
  starterCode: string;
  readOnly: boolean;
  onUnsavedChange?: (unsaved: boolean) => void;
  renderSubmission?: (source: string) => ReactNode;
  /** 编辑区与运行区怎么摆由调用方决定（宽屏是可拖动的上下两栏）；默认上下堆叠。 */
  layout?: (editor: ReactNode, submission: ReactNode) => ReactNode;
  /** 放在工具行左侧的内容（语言与编码模式），与右侧的代码操作同一行。 */
  toolbarStart?: ReactNode;
};

export function DraftEditor({
  readOnly,
  onUnsavedChange,
  renderSubmission,
  historyRef,
  toolbarStart,
  layout = (editor, submission) => (
    <>
      {editor}
      {submission}
    </>
  ),
  ...identity
}: DraftEditorProps) {
  const draft = useCodeDraft(identity);
  const [pendingLoad, setPendingLoad] = useState<{ value: HistoryLoad; original: string } | null>(
    null,
  );
  useImperativeHandle(historyRef, () => ({
    requestLoad(value) {
      if (
        readOnly ||
        value.userId !== identity.userId ||
        value.source.problemId !== identity.problemId ||
        value.source.languageId !== identity.languageId
      )
        return;
      setPendingLoad({ value, original: draft.value });
    },
  }));
  if (readOnly && pendingLoad) setPendingLoad(null);
  const loadChanged =
    pendingLoad !== null && (pendingLoad.original !== draft.value || draft.status === 'conflict');
  const [resetOpen, setResetOpen] = useState(false);
  const [conflictOpen, setConflictOpen] = useState(false);
  const blocker = useBlocker({
    shouldBlockFn: ({ current, next }) => current.pathname !== next.pathname && !draft.flush(),
    enableBeforeUnload: false,
    withResolver: true,
  });
  useEffect(() => {
    onUnsavedChange?.(draft.hasUnsavedChanges);
    return () => onUnsavedChange?.(false);
  }, [draft.hasUnsavedChanges, onUnsavedChange]);
  const exportSource = () => {
    const url = URL.createObjectURL(new Blob([draft.value], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'solution.cpp';
    link.click();
    URL.revokeObjectURL(url);
  };
  return (
    <>
      {layout(
        <>
          {/* 保存状态与语言同一行，不单独占一行；出问题时才变成醒目的文字和按钮。 */}
          <PaneHeader
            start={
              <>
                {toolbarStart}
                <span
                  role="status"
                  className={cn(
                    'min-w-0 truncate',
                    draft.status === 'error' || draft.status === 'conflict'
                      ? 'text-danger'
                      : 'text-fg-disabled',
                  )}
                >
                  {draft.message}
                </span>
                {draft.status === 'error' ? (
                  <Button size="sm" variant="secondary" onClick={draft.retrySave}>
                    重试保存
                  </Button>
                ) : null}
                {draft.status === 'conflict' ? (
                  <Button size="sm" variant="secondary" onClick={() => setConflictOpen(true)}>
                    查看恢复副本
                  </Button>
                ) : null}
              </>
            }
          >
            <CopyIconButton value={draft.value} label="复制代码" />
            <IconButton size="sm" label="下载代码" onClick={exportSource}>
              <Download className="size-3.5" aria-hidden="true" />
            </IconButton>
            <IconButton
              size="sm"
              label="恢复起始代码"
              disabled={readOnly}
              onClick={() => setResetOpen(true)}
            >
              <RotateCcw className="size-3.5" aria-hidden="true" />
            </IconButton>
          </PaneHeader>
          <SourceEditor value={draft.value} onChange={draft.setValue} readOnly={readOnly} />
        </>,
        renderSubmission?.(draft.value),
      )}
      <Dialog
        open={pendingLoad !== null && !readOnly}
        onOpenChange={(open) => {
          if (!open) setPendingLoad(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>载入历史代码？</DialogTitle>
            <DialogDescription>
              将使用这次提交的代码替换右侧当前草稿。需要保留时，请先取消并复制或下载。
              新提交按题目当前的测试数据判题。
            </DialogDescription>
          </DialogHeader>
          {loadChanged ? (
            <p role="alert" className="text-fg-2 text-sm">
              草稿已变化或存在多标签冲突，请取消、处理后重新载入。
            </p>
          ) : null}
          <DialogFooter>
            <Button variant="secondary" onClick={() => setPendingLoad(null)}>
              取消
            </Button>
            <Button
              disabled={readOnly || loadChanged}
              onClick={() => {
                if (
                  !pendingLoad ||
                  readOnly ||
                  loadChanged ||
                  pendingLoad.value.userId !== identity.userId
                )
                  return;
                draft.setValue(pendingLoad.value.source.source);
                setPendingLoad(null);
              }}
            >
              确认载入
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <Dialog open={resetOpen} onOpenChange={setResetOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>恢复起始代码？</DialogTitle>
            <DialogDescription>
              将替换当前账号、当前题目版本的代码。需要保留时，请先复制或下载。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="secondary" onClick={() => setResetOpen(false)}>
              取消
            </Button>
            <Button
              disabled={readOnly}
              onClick={() => {
                draft.resetToStarter();
                setResetOpen(false);
              }}
            >
              确认恢复
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <Dialog open={conflictOpen} onOpenChange={setConflictOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>选择要继续编辑的代码</DialogTitle>
            <DialogDescription>
              另一标签页也修改了草稿。以下副本可复制备份，选择后才会合并到当前草稿。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <p className="text-sm">当前编辑内容</p>
              <pre className="bg-surface-subtle max-h-40 overflow-auto p-3 font-mono text-xs">
                <code>{draft.value || '（空代码）'}</code>
              </pre>
              <Button
                variant="secondary"
                disabled={readOnly}
                onClick={() => {
                  draft.resolveConflict('current');
                  setConflictOpen(false);
                }}
              >
                保留当前内容
              </Button>
            </div>
            {draft.conflicts.map((copy, index) => (
              <div key={copy.revision} className="space-y-2">
                <p className="text-fg-muted text-xs">
                  恢复副本 {index + 1} · {new Date(copy.updatedAt).toLocaleString()}
                </p>
                <pre className="bg-surface-subtle max-h-40 overflow-auto p-3 font-mono text-xs">
                  <code>{copy.source || '（空代码）'}</code>
                </pre>
                <div className="flex flex-wrap gap-2">
                  <SourceCopyButton value={copy.source} label={`复制恢复副本 ${index + 1}`} />
                  <Button
                    variant="secondary"
                    disabled={readOnly}
                    onClick={() => {
                      draft.resolveConflict(copy.revision);
                      setConflictOpen(false);
                    }}
                  >
                    使用恢复副本 {index + 1}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </DialogContent>
      </Dialog>
      <Dialog
        open={blocker.status === 'blocked'}
        onOpenChange={(open) => {
          if (!open) blocker.reset?.();
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>代码还未保存到本机</DialogTitle>
            <DialogDescription>
              请返回复制或下载备份。继续离开可能丢失尚未保存的修改。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="secondary" onClick={() => blocker.reset?.()}>
              返回编辑
            </Button>
            <Button variant="danger" onClick={() => blocker.proceed?.()}>
              仍然离开
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
